import { writeFileSync } from "node:fs";
import { renderOtpEmail } from "./src/lib/mailer.ts";

const html = renderOtpEmail({
  name: "Shivam Thorat",
  otp: "846186",
  to: "shivamthorat00@gmail.com",
});

writeFileSync("preview-otp.html", html, "utf8");
console.log("preview written:", html.length, "chars");
