/*
 * Password rules, mirrored from the server.
 *
 * WHY THIS EXISTS WHEN THE SERVER ALREADY CHECKS
 *
 * It does not replace the server check - PasswordPolicy.java is the rule that
 * actually holds, because anyone can call the API directly and skip this form
 * entirely.
 *
 * This exists for feedback. Without it, someone types a password, fills in the
 * rest of the form, presses the button, waits for a round trip, and only then
 * learns their password was rejected. Telling them while they type is simply
 * kinder.
 *
 * The two must be kept in step. If the server rules change, change these too -
 * the failure mode is a form that says "looks good" and then a server that
 * disagrees, which is worse than no feedback at all.
 */

export const MIN_PASSWORD_LENGTH = 8

/*
 * Same list as the server's. Short on purpose: these are the ones an attacker
 * genuinely tries first, and shipping a list of a million to the browser would
 * be absurd.
 */
const COMMON = new Set([
  'password', 'password1', 'password123', 'passw0rd',
  '12345678', '123456789', '1234567890', 'qwerty123', 'qwertyuiop',
  'letmein', 'welcome1', 'admin123', 'iloveyou', 'abc12345',
  'football', 'monkey123', 'sunshine', 'princess', 'dragon123',
  'trustno1', 'baseball', 'superman', 'starwars', 'whatever',
])

export interface PasswordStrength {
  /* 0-4, drives the bars in the UI. */
  score: number
  /* A word for the score, shown when the password is acceptable. */
  label: string
  /* Whether it passes the rules. */
  acceptable: boolean
  /* What is wrong, when it is not acceptable. Shown instead of the label. */
  problem?: string
}

/*
 * NOTE ON WHAT IS NOT REQUIRED
 *
 * No mandatory uppercase, digit or symbol. Those rules sound strict and produce
 * "Password1!" - which satisfies all of them and is among the first guesses any
 * attacker makes. Length and unpredictability are what actually help, so a long
 * passphrase is welcome here and a short cryptic one is not.
 */
export function passwordStrength(password: string): PasswordStrength {
  if (!password) {
    return { score: 0, label: '', acceptable: false }
  }

  if (password.length < MIN_PASSWORD_LENGTH) {
    const needed = MIN_PASSWORD_LENGTH - password.length
    return {
      score: 0,
      label: 'Too short',
      acceptable: false,
      problem: `${needed} more character${needed === 1 ? '' : 's'} needed`,
    }
  }

  if (COMMON.has(password.toLowerCase())) {
    return {
      score: 0,
      label: 'Very common',
      acceptable: false,
      problem: 'That is one of the most common passwords. Please pick another.',
    }
  }

  const distinct = new Set(password).size

  if (distinct < 4) {
    return {
      score: 0,
      label: 'Too repetitive',
      acceptable: false,
      problem: 'Needs a bit more variety in it',
    }
  }

  let score = 1
  if (password.length >= 12) score++
  if (distinct >= 8) score++
  if (/[^a-zA-Z0-9]/.test(password) || password.length >= 16) score++

  const labels = ['', 'Okay', 'Good', 'Strong', 'Very strong']

  return {
    score: Math.min(score, 4),
    label: labels[Math.min(score, 4)],
    acceptable: true,
  }
}
