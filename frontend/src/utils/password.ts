export const PASSWORD_MIN_LENGTH = 12

export interface PasswordChecks {
  length: boolean
  upper: boolean
  lower: boolean
  digit: boolean
  symbol: boolean
}

export function passwordChecks(pw: string): PasswordChecks {
  return {
    length: pw.length >= PASSWORD_MIN_LENGTH,
    upper: /[A-Z]/.test(pw),
    lower: /[a-z]/.test(pw),
    digit: /[0-9]/.test(pw),
    symbol: /[^A-Za-z0-9]/.test(pw),
  }
}

export function passwordValid(pw: string): boolean {
  const c = passwordChecks(pw)
  const classes = [c.upper, c.lower, c.digit, c.symbol].filter(Boolean).length
  return c.length && classes >= 3
}
