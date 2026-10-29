# BUG-036: A pasted login code with a leading space loses its last digit

- **Severity:** major
- **Area:** sign-in code (`codeForm`) and the email-change code (`emailCodePage`)

## Steps to reproduce
1. Ask for a sign-in code.
2. Copy the code from the mail so the copy picks up a space in front (" 123456"), and paste it into the code field.

## Expected
The field holds "123456" and signing in works.

## Actual
The field holds " 12345": `maxlength="6"` cuts the paste before anything sees it, so the last digit is gone. The server already keeps only digits, but by then there are five.

## Status
Status: fixed (2026-10-29). Both code fields use the shared `codeInput`: no `maxlength`, and on every input the browser keeps only the digits, at most six. The server still ignores anything else.
