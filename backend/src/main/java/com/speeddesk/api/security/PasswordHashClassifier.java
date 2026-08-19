package com.speeddesk.api.security;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PasswordHashClassifier {

    private static final String BCRYPT_PREFIX = "{bcrypt}";
    private static final Pattern BCRYPT = Pattern.compile(
            "^\\$2[aby]\\$(?:0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}$"
    );
    private static final Pattern COMMON_HEX_DIGEST = Pattern.compile(
            "^(?:[0-9a-fA-F]{32}|[0-9a-fA-F]{40}|[0-9a-fA-F]{64}|"
                    + "[0-9a-fA-F]{96}|[0-9a-fA-F]{128})$"
    );

    public StoredPassword classify(String storedPassword) {
        if (storedPassword == null || storedPassword.isEmpty()) {
            return new StoredPassword(PasswordFormat.UNSUPPORTED_HASH, null);
        }

        String bcryptCandidate = storedPassword.startsWith(BCRYPT_PREFIX)
                ? storedPassword.substring(BCRYPT_PREFIX.length())
                : storedPassword;

        if (BCRYPT.matcher(bcryptCandidate).matches()) {
            return new StoredPassword(PasswordFormat.BCRYPT, bcryptCandidate);
        }

        if (storedPassword.startsWith("{")
                || storedPassword.startsWith("$")
                || COMMON_HEX_DIGEST.matcher(storedPassword).matches()) {
            return new StoredPassword(PasswordFormat.UNSUPPORTED_HASH, null);
        }

        return new StoredPassword(PasswordFormat.LEGACY_PLAINTEXT, storedPassword);
    }

    public enum PasswordFormat {
        BCRYPT,
        LEGACY_PLAINTEXT,
        UNSUPPORTED_HASH
    }

    public record StoredPassword(PasswordFormat format, String value) {
    }
}
