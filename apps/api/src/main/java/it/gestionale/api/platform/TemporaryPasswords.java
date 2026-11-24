package it.gestionale.api.platform;

import java.security.SecureRandom;

/** Password provvisorie: 16 caratteri casuali, senza caratteri ambigui (0/O, 1/l/I). */
final class TemporaryPasswords {

    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String ALL = LOWER + UPPER + DIGITS;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {
    }

    static String generate() {
        StringBuilder password = new StringBuilder(16);
        // Almeno una minuscola, una maiuscola e una cifra.
        password.append(pick(LOWER)).append(pick(UPPER)).append(pick(DIGITS));
        while (password.length() < 16) {
            password.append(pick(ALL));
        }
        for (int i = password.length() - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char c = password.charAt(i);
            password.setCharAt(i, password.charAt(j));
            password.setCharAt(j, c);
        }
        return password.toString();
    }

    private static char pick(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }
}
