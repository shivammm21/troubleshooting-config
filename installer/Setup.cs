// StackPilot Windows setup bootstrapper.
//
// Compiled and concatenated with the packaged application image by
// build-installer.ps1, producing a single self-contained .exe:
//
//     [ this bootstrapper ][ payload .zip ][ 8-byte payload length ][ "SPST" ]
//
// Modes:
//     (none)               → interactive installer UI
//     --uninstall          → interactive uninstaller
//     --silent             → install without any UI (logs to the console)
//     --extract <dir>      → unpack the payload only (used by build verification)

using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;
using Microsoft.Win32;

namespace StackPilot.Setup
{
    internal static class Program
    {
        internal const string AppName = "StackPilot";
        internal const string AppVersion = "1.0.0";
        internal const string Publisher = "StackPilot";
        internal const string AppDescription = "Documentation-aware DevOps troubleshooting tool";
        internal const string LauncherExe = "StackPilot.exe";
        internal const string UninstallExe = "Uninstall.exe";
        internal const string UninstallKey =
            @"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\StackPilot";

        /// <summary>Trailer magic written by build-installer.ps1.</summary>
        internal static readonly byte[] Magic = { 0x53, 0x50, 0x53, 0x54 }; // "SPST"

        [STAThread]
        private static int Main(string[] args)
        {
            try
            {
                if (args.Length >= 2 && IsFlag(args[0], "--extract"))
                {
                    // Extracts the payload into <dir> and stops – verification only.
                    long bytes = Installer.ExtractPayload(args[1], null);
                    Console.WriteLine("Extracted " + bytes + " bytes to " + args[1]);
                    return 0;
                }

                if (args.Length >= 1 && IsFlag(args[0], "--uninstall"))
                {
                    return Uninstaller.Run();
                }

                if (args.Length >= 1 && IsFlag(args[0], "--silent"))
                {
                    Installer.Install(null);
                    return 0;
                }

                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                Application.Run(new SetupForm());
                return Environment.ExitCode;
            }
            catch (Exception ex)
            {
                ShowError(ex);
                return 1;
            }
        }

        internal static bool IsFlag(string value, string flag)
        {
            return string.Equals(value, flag, StringComparison.OrdinalIgnoreCase);
        }

        internal static void ShowError(Exception ex)
        {
            MessageBox.Show(
                "StackPilot setup could not complete.\r\n\r\n" + ex.Message,
                AppName + " Setup",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
        }

        internal static string InstallDirectory
        {
            get
            {
                return Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
                    AppName);
            }
        }

        internal static string DesktopShortcutPath
        {
            get
            {
                return Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.CommonDesktopDirectory),
                    AppName + ".lnk");
            }
        }

        internal static string StartMenuFolder
        {
            get
            {
                return Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.CommonPrograms),
                    AppName);
            }
        }
    }

    /// <summary>Locates, unpacks and installs the payload appended to this executable.</summary>
    internal static class Installer
    {
        internal sealed class PayloadInfo
        {
            public long Offset;
            public long Length;
        }

        internal static string SelfPath
        {
            get { return Assembly.GetExecutingAssembly().Location; }
        }

        /// <summary>Reads the trailing payload descriptor from this executable.</summary>
        internal static PayloadInfo ReadPayloadInfo()
        {
            using (FileStream fs = new FileStream(SelfPath, FileMode.Open, FileAccess.Read, FileShare.Read))
            {
                if (fs.Length < 4096)
                {
                    throw new InvalidDataException(
                        "This executable does not contain a StackPilot payload.");
                }

                fs.Seek(-12, SeekOrigin.End);
                byte[] tail = new byte[12];
                if (fs.Read(tail, 0, 12) != 12)
                {
                    throw new InvalidDataException("Setup payload descriptor is truncated.");
                }

                for (int i = 0; i < Program.Magic.Length; i++)
                {
                    if (tail[8 + i] != Program.Magic[i])
                    {
                        throw new InvalidDataException(
                            "This executable does not contain a StackPilot payload.");
                    }
                }

                long length = BitConverter.ToInt64(tail, 0);
                long offset = fs.Length - 12 - length;
                if (length <= 0 || offset <= 0)
                {
                    throw new InvalidDataException("Setup payload descriptor is invalid.");
                }

                return new PayloadInfo { Offset = offset, Length = length };
            }
        }

        /// <summary>Copies the appended zip payload into a temporary file.</summary>
        private static string CopyPayloadToTemp(PayloadInfo payload)
        {
            string temp = Path.Combine(
                Path.GetTempPath(),
                "stackpilot-payload-" + Guid.NewGuid().ToString("N") + ".zip");

            using (FileStream src = new FileStream(SelfPath, FileMode.Open, FileAccess.Read, FileShare.Read))
            using (FileStream dst = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            {
                src.Seek(payload.Offset, SeekOrigin.Begin);
                byte[] buffer = new byte[256 * 1024];
                long remaining = payload.Length;

                while (remaining > 0)
                {
                    int want = (int)Math.Min(buffer.Length, remaining);
                    int read = src.Read(buffer, 0, want);
                    if (read <= 0)
                    {
                        throw new EndOfStreamException("Setup payload is truncated.");
                    }
                    dst.Write(buffer, 0, read);
                    remaining -= read;
                }
            }

            return temp;
        }

        /// <summary>
        /// Unpacks the payload into <paramref name="destinationRoot"/>.
        /// The archive stores a single top-level "StackPilot" folder.
        /// </summary>
        internal static long ExtractPayload(string destinationRoot, Action<long, long> progress)
        {
            PayloadInfo payload = ReadPayloadInfo();
            string zipPath = CopyPayloadToTemp(payload);

            try
            {
                Directory.CreateDirectory(destinationRoot);
                long totalWritten = 0;

                using (ZipArchive archive = ZipFile.OpenRead(zipPath))
                {
                    bool sawRoot = false;
                    foreach (ZipArchiveEntry entry in archive.Entries)
                    {
                        // Normalise separators – some zip producers store '\'.
                        string name = entry.FullName.Replace('\\', '/');

                        if (!sawRoot)
                        {
                            if (!name.StartsWith(Program.AppName + "/", StringComparison.OrdinalIgnoreCase))
                            {
                                throw new InvalidDataException(
                                    "Setup payload has an unexpected layout: " + entry.FullName);
                            }
                            sawRoot = true;
                        }

                        string relative = name.Replace('/', Path.DirectorySeparatorChar);
                        string target = Path.GetFullPath(Path.Combine(destinationRoot, relative));
                        string rootFull = Path.GetFullPath(destinationRoot);
                        if (!target.StartsWith(rootFull, StringComparison.OrdinalIgnoreCase))
                        {
                            throw new InvalidDataException("Setup payload contains an unsafe path.");
                        }

                        if (name.EndsWith("/"))
                        {
                            Directory.CreateDirectory(target);
                            continue;
                        }

                        string parent = Path.GetDirectoryName(target);
                        if (!string.IsNullOrEmpty(parent))
                        {
                            Directory.CreateDirectory(parent);
                        }

                        using (Stream input = entry.Open())
                        using (FileStream output = new FileStream(target, FileMode.Create, FileAccess.Write, FileShare.None))
                        {
                            input.CopyTo(output, 256 * 1024);
                        }

                        totalWritten += entry.Length;
                        if (progress != null)
                        {
                            progress(totalWritten, Math.Max(payload.Length, totalWritten));
                        }
                    }

                    if (!sawRoot)
                    {
                        throw new InvalidDataException("Setup payload is empty.");
                    }
                }

                return totalWritten;
            }
            finally
            {
                TryDeleteFile(zipPath);
            }
        }

        /// <summary>Full install: unpack, place files, create shortcuts and register uninstall info.</summary>
        internal static void Install(Action<string, int> report)
        {
            string installDir = Program.InstallDirectory;
            string staging = Path.Combine(
                Path.GetTempPath(),
                "stackpilot-setup-" + Guid.NewGuid().ToString("N"));

            try
            {
                Notify(report, "Preparing installation…", 2);
                Directory.CreateDirectory(staging);

                // 1. Unpack into a staging directory first so a failed extraction
                //    never leaves a half-written Program Files folder behind.
                Notify(report, "Unpacking StackPilot…", 5);
                ExtractPayload(staging, (written, total) =>
                {
                    int percent = 5 + (int)Math.Min(70, written * 70L / Math.Max(total, 1));
                    Notify(report, "Unpacking StackPilot…", percent);
                });

                string stagedApp = Path.Combine(staging, Program.AppName);
                string launcher = Path.Combine(stagedApp, Program.LauncherExe);
                if (!File.Exists(launcher))
                {
                    throw new InvalidDataException("Payload does not contain " + Program.LauncherExe + ".");
                }

                // 2. Replace any previous installation.
                Notify(report, "Replacing previous installation…", 78);
                if (Directory.Exists(installDir))
                {
                    DeleteDirectory(installDir);
                }

                string installParent = Path.GetDirectoryName(installDir);
                if (!string.IsNullOrEmpty(installParent))
                {
                    Directory.CreateDirectory(installParent);
                }

                try
                {
                    // Same volume: instant rename.
                    Directory.Move(stagedApp, installDir);
                }
                catch (IOException)
                {
                    // Staging (%TEMP%) can sit on a different volume than
                    // Program Files – fall back to a copy.
                    CopyDirectory(stagedApp, installDir);
                }
                // 3. Uninstaller = this executable without its payload.
                Notify(report, "Registering application…", 88);
                WriteUninstaller(installDir);

                // 4. Shortcuts + Add/Remove Programs entry.
                CreateShortcuts(installDir);
                WriteUninstallEntry(installDir, DirectorySize(installDir));

                Notify(report, "Finished", 100);
            }
            finally
            {
                DeleteDirectory(staging);
            }
        }

        private static void Notify(Action<string, int> report, string message, int percent)
        {
            if (report != null)
            {
                report(message, Math.Max(0, Math.Min(100, percent)));
            }
        }

        /// <summary>Recursive directory copy used when a rename is not possible.</summary>
        private static void CopyDirectory(string source, string destination)
        {
            Directory.CreateDirectory(destination);

            foreach (string file in Directory.GetFiles(source))
            {
                File.Copy(file, Path.Combine(destination, Path.GetFileName(file)), true);
            }

            foreach (string directory in Directory.GetDirectories(source))
            {
                CopyDirectory(directory, Path.Combine(destination, Path.GetFileName(directory)));
            }
        }

        /// <summary>Writes a small Uninstall.exe (this bootstrapper minus the payload).</summary>
        private static void WriteUninstaller(string installDir)
        {
            long payloadLength;
            using (FileStream probe = new FileStream(SelfPath, FileMode.Open, FileAccess.Read, FileShare.Read))
            {
                probe.Seek(-12, SeekOrigin.End);
                byte[] tail = new byte[12];
                if (probe.Read(tail, 0, 12) != 12)
                {
                    return;
                }
                payloadLength = BitConverter.ToInt64(tail, 0);
            }

            long stubLength;
            using (FileStream probe = new FileStream(SelfPath, FileMode.Open, FileAccess.Read, FileShare.Read))
            {
                stubLength = probe.Length - 12 - payloadLength;
            }

            if (stubLength <= 0)
            {
                return;
            }

            using (FileStream src = new FileStream(SelfPath, FileMode.Open, FileAccess.Read, FileShare.Read))
            using (FileStream dst = new FileStream(
                Path.Combine(installDir, Program.UninstallExe),
                FileMode.Create,
                FileAccess.Write,
                FileShare.None))
            {
                byte[] buffer = new byte[256 * 1024];
                long remaining = stubLength;
                while (remaining > 0)
                {
                    int want = (int)Math.Min(buffer.Length, remaining);
                    int read = src.Read(buffer, 0, want);
                    if (read <= 0)
                    {
                        break;
                    }
                    dst.Write(buffer, 0, read);
                    remaining -= read;
                }
            }
        }

        private static void CreateShortcuts(string installDir)
        {
            string launcher = Path.Combine(installDir, Program.LauncherExe);

            CreateShortcut(Program.DesktopShortcutPath, launcher, installDir, launcher);

            Directory.CreateDirectory(Program.StartMenuFolder);
            CreateShortcut(
                Path.Combine(Program.StartMenuFolder, Program.AppName + ".lnk"),
                launcher,
                installDir,
                launcher);
            CreateShortcut(
                Path.Combine(Program.StartMenuFolder, "Uninstall " + Program.AppName + ".lnk"),
                Path.Combine(installDir, Program.UninstallExe),
                installDir,
                launcher);
        }

        private static void CreateShortcut(string linkPath, string targetPath, string workingDir, string iconPath)
        {
            Type shellType = Type.GetTypeFromProgID("WScript.Shell");
            if (shellType == null)
            {
                return;
            }

            dynamic shell = Activator.CreateInstance(shellType);
            dynamic link = shell.CreateShortcut(linkPath);
            link.TargetPath = targetPath;
            link.WorkingDirectory = workingDir;
            link.IconLocation = iconPath + ",0";
            link.Description = Program.AppDescription;
            link.Save();
        }

        private static void WriteUninstallEntry(string installDir, long installedBytes)
        {
            using (RegistryKey key = Registry.LocalMachine.CreateSubKey(Program.UninstallKey))
            {
                if (key == null)
                {
                    return;
                }

                string launcher = Path.Combine(installDir, Program.LauncherExe);
                string uninstall = "\"" + Path.Combine(installDir, Program.UninstallExe) + "\" --uninstall";

                key.SetValue("DisplayName", Program.AppName);
                key.SetValue("DisplayVersion", Program.AppVersion);
                key.SetValue("Publisher", Program.Publisher);
                key.SetValue("InstallLocation", installDir);
                key.SetValue("DisplayIcon", launcher + ",0");
                key.SetValue("UninstallString", uninstall);
                key.SetValue("QuietUninstallString", uninstall);
                key.SetValue("InstallDate", DateTime.Now.ToString("yyyyMMdd"));
                key.SetValue("EstimatedSize", (int)Math.Max(1, installedBytes / 1024), RegistryValueKind.DWord);
                key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
            }
        }

        internal static long DirectorySize(string path)
        {
            long total = 0;
            try
            {
                foreach (string file in Directory.GetFiles(path, "*", SearchOption.AllDirectories))
                {
                    try
                    {
                        total += new FileInfo(file).Length;
                    }
                    catch
                    {
                        // Ignore files that disappear mid-count.
                    }
                }
            }
            catch
            {
                // Sizing is cosmetic – never fail the install over it.
            }
            return total;
        }

        /// <summary>Best-effort recursive delete that survives read-only files and locks.</summary>
        internal static void DeleteDirectory(string path)
        {
            if (!Directory.Exists(path))
            {
                return;
            }

            try
            {
                Directory.Delete(path, true);
                return;
            }
            catch
            {
                // Fall through to the file-by-file retry.
            }

            foreach (string file in Directory.GetFiles(path, "*", SearchOption.AllDirectories))
            {
                TryDeleteFile(file);
            }

            foreach (string dir in Directory.GetDirectories(path, "*", SearchOption.AllDirectories))
            {
                try { Directory.Delete(dir, true); } catch { }
            }

            try { Directory.Delete(path, true); } catch { }
        }

        internal static void TryDeleteFile(string path)
        {
            try
            {
                if (File.Exists(path))
                {
                    File.SetAttributes(path, FileAttributes.Normal);
                    File.Delete(path);
                }
            }
            catch
            {
                // Best effort only.
            }
        }
    }

    /// <summary>Interactive installer window.</summary>
    internal sealed class SetupForm : Form
    {
        private readonly Label _message = new Label();
        private readonly Label _target = new Label();
        private readonly ProgressBar _progress = new ProgressBar();
        private readonly Button _install = new Button();
        private readonly Button _cancel = new Button();
        private volatile bool _running;

        public SetupForm()
        {
            Text = Program.AppName + " Setup";
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            ClientSize = new Size(520, 200);
            Font = new Font("Segoe UI", 9f);
            BackColor = Color.White;
            Icon = Icon.ExtractAssociatedIcon(Installer.SelfPath);

            Label title = new Label
            {
                Text = "Install " + Program.AppName + " " + Program.AppVersion,
                Font = new Font("Segoe UI", 14f, FontStyle.Bold),
                Location = new Point(24, 20),
                AutoSize = true
            };

            _message.Text = "StackPilot installs for all users on this computer.";
            _message.Location = new Point(26, 62);
            _message.AutoSize = true;

            _target.Text = "Destination: " + Program.InstallDirectory;
            _target.Location = new Point(26, 86);
            _target.Size = new Size(468, 20);
            _target.AutoEllipsis = true;
            _target.ForeColor = Color.DimGray;

            _progress.Location = new Point(26, 118);
            _progress.Size = new Size(466, 18);
            _progress.Style = ProgressBarStyle.Continuous;
            _progress.Minimum = 0;
            _progress.Maximum = 100;

            _install.Text = "Install";
            _install.Location = new Point(326, 152);
            _install.Size = new Size(80, 30);
            _install.Click += OnInstallClick;

            _cancel.Text = "Cancel";
            _cancel.Location = new Point(412, 152);
            _cancel.Size = new Size(80, 30);
            _cancel.Click += OnCancelClick;

            Controls.Add(title);
            Controls.Add(_message);
            Controls.Add(_target);
            Controls.Add(_progress);
            Controls.Add(_install);
            Controls.Add(_cancel);

            AcceptButton = _install;
            CancelButton = _cancel;

            FormClosing += (sender, e) =>
            {
                if (_running)
                {
                    e.Cancel = true;
                }
            };
        }

        private void OnCancelClick(object sender, EventArgs e)
        {
            if (!_running)
            {
                Close();
            }
        }

        private void OnInstallClick(object sender, EventArgs e)
        {
            _running = true;
            _install.Enabled = false;
            _cancel.Enabled = false;

            foreach (Process process in Process.GetProcessesByName(Program.AppName))
            {
                try
                {
                    process.Kill();
                    process.WaitForExit(5000);
                }
                catch
                {
                    // A running instance that cannot be closed is reported later.
                }
            }

            Thread worker = new Thread(InstallWorker);
            worker.IsBackground = true;
            worker.Start();
        }

        private void InstallWorker()
        {
            try
            {
                Installer.Install(Report);
                BeginInvoke(new Action(OnInstallFinished));
            }
            catch (Exception ex)
            {
                BeginInvoke(new Action(() => OnInstallFailed(ex)));
            }
        }

        private void Report(string message, int percent)
        {
            if (IsDisposed)
            {
                return;
            }

            BeginInvoke(new Action(() =>
            {
                _message.Text = message;
                _progress.Value = Math.Max(_progress.Minimum, Math.Min(_progress.Maximum, percent));
            }));
        }

        private void OnInstallFinished()
        {
            _running = false;
            _message.Text = "StackPilot is installed.";
            _progress.Value = 100;

            DialogResult launch = MessageBox.Show(
                "StackPilot " + Program.AppVersion + " is installed.\r\n\r\nLaunch it now?",
                Program.AppName,
                MessageBoxButtons.YesNo,
                MessageBoxIcon.Information);

            if (launch == DialogResult.Yes)
            {
                try
                {
                    Process.Start(new ProcessStartInfo(
                        Path.Combine(Program.InstallDirectory, Program.LauncherExe))
                    {
                        WorkingDirectory = Program.InstallDirectory
                    });
                }
                catch (Exception ex)
                {
                    MessageBox.Show(ex.Message, Program.AppName, MessageBoxButtons.OK, MessageBoxIcon.Warning);
                }
            }

            Close();
        }

        private void OnInstallFailed(Exception ex)
        {
            _running = false;
            _install.Enabled = true;
            _cancel.Enabled = true;
            _progress.Value = 0;
            _message.Text = "Installation failed.";
            MessageBox.Show(
                "StackPilot could not be installed.\r\n\r\n" + ex.Message,
                Program.AppName + " Setup",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error);
        }
    }

    /// <summary>Removes files, shortcuts and the Add/Remove Programs entry.</summary>
    internal static class Uninstaller
    {
        internal static int Run()
        {
            string installDir = Program.InstallDirectory;

            DialogResult confirm = MessageBox.Show(
                "Remove " + Program.AppName + " and all of its files from this computer?",
                Program.AppName + " Uninstall",
                MessageBoxButtons.YesNo,
                MessageBoxIcon.Question);

            if (confirm != DialogResult.Yes)
            {
                return 0;
            }

            foreach (Process process in Process.GetProcessesByName(Program.AppName))
            {
                try
                {
                    process.Kill();
                    process.WaitForExit(5000);
                }
                catch
                {
                    // Keep going – the delete step reports failures.
                }
            }

            try
            {
                Registry.LocalMachine.DeleteSubKeyTree(Program.UninstallKey, false);
            }
            catch
            {
                // Missing key is fine.
            }

            Installer.TryDeleteFile(Program.DesktopShortcutPath);

            try
            {
                if (Directory.Exists(Program.StartMenuFolder))
                {
                    foreach (string file in Directory.GetFiles(Program.StartMenuFolder))
                    {
                        Installer.TryDeleteFile(file);
                    }
                    Directory.Delete(Program.StartMenuFolder, true);
                }
            }
            catch
            {
                // Shortcuts are best effort.
            }

            // This executable lives inside the folder it must delete, so hand the
            // delete off to a detached shell that runs after we exit.
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo("cmd.exe")
                {
                    Arguments = "/c ping -n 3 127.0.0.1 > nul & rmdir /s /q \"" + installDir + "\"",
                    CreateNoWindow = true,
                    UseShellExecute = false,
                    WindowStyle = ProcessWindowStyle.Hidden
                };
                Process.Start(psi);
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "StackPilot was unregistered but its folder could not be removed:\r\n" + installDir +
                    "\r\n\r\n" + ex.Message,
                    Program.AppName + " Uninstall",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Warning);
            }

            MessageBox.Show(
                Program.AppName + " has been removed.",
                Program.AppName + " Uninstall",
                MessageBoxButtons.OK,
                MessageBoxIcon.Information);

            return 0;
        }
    }
}
