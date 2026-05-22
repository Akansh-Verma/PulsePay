import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "PulsePay",
  description: "Thin frontend dashboard for the PulsePay payment service"
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
