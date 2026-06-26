import { describe, it, expect } from 'vitest'
import { passwordChecks, passwordValid, PASSWORD_MIN_LENGTH } from '@/utils/password'

describe('password policy (frontend mirror)', () => {
  it('min length is 12', () => {
    expect(PASSWORD_MIN_LENGTH).toBe(12)
  })
  it('accepts 12 chars with 3 classes', () => {
    expect(passwordValid('Abcdefgh1234')).toBe(true)
  })
  it('rejects too short', () => {
    expect(passwordValid('Abc123!')).toBe(false)
  })
  it('rejects too few classes', () => {
    expect(passwordValid('abcdefghijkl')).toBe(false)
  })
  it('reports individual checks', () => {
    const c = passwordChecks('abcdefgh12!@')
    expect(c.length).toBe(true)
    expect(c.lower).toBe(true)
    expect(c.digit).toBe(true)
    expect(c.symbol).toBe(true)
    expect(c.upper).toBe(false)
  })
})
