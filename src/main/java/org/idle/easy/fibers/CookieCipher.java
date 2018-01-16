package org.idle.easy.fibers;

import co.paralleluniverse.fibers.Suspendable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Created by Nikolay Petkov .
 */
public class CookieCipher {

    static private final Logger logger = LoggerFactory.getLogger(CookieCipher.class);
    private Key key;
    private Cipher cipherEncrypt;
    private Cipher cipherDecrypt;

    public CookieCipher() {
        try {
            final String cookieKey = "O6I7GpCGDVxaayMqwdHIa9iv02bIssUf";
            key = new SecretKeySpec(cookieKey.getBytes(StandardCharsets.UTF_8), "Blowfish");
            cipherEncrypt = Cipher.getInstance("Blowfish/ECB/NoPadding");
            cipherDecrypt = Cipher.getInstance("Blowfish/ECB/NoPadding");
            cipherEncrypt.init(Cipher.ENCRYPT_MODE, key);
            cipherDecrypt.init(Cipher.DECRYPT_MODE, key);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            logger.error("Problem when creating Cipher for cookie", e);
        }
    }

    private String zeroPadMultiple(String source) {
        int size = (int) Math.ceil((double) source.length() / 8) * 8;
        StringBuilder sb = new StringBuilder(source);
        for (int i = source.length(); i < size; i++) {
            sb.append("\u0000");
        }
        return sb.toString();
    }

    private String zeroTrim(String source) {
        return source.replaceAll("\u0000", "");
    }

    @Suspendable
    public String encryptCookie(String source) {
        logger.info("cookie source: {}", source);
        String encryped = null;
        try {
            source = zeroPadMultiple(source);
            final byte[] encrypted = cipherEncrypt.update(source.getBytes(StandardCharsets.UTF_8.name())); 
            encryped = Base64.getEncoder().encodeToString(encrypted);
            encryped = URLEncoder.encode(encryped, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            logger.error("Problem when decrypt UserContext from cookie", e);
        }
        logger.info("cookie encrypted: {}", encryped);
        return encryped;
    }

    @Suspendable
    public String decryptCookie(String cookieValue) {
        String decrypted = null;
        try {
            String decode = URLDecoder.decode(cookieValue, StandardCharsets.UTF_8.name());
            byte[] encryptedBytes;
            try {
                encryptedBytes = Base64.getDecoder().decode(decode);
            } catch (IllegalArgumentException iae) {
                // if fails try the non-decoded version
                // some of the cookies generated won't work with decoded value
                encryptedBytes = Base64.getDecoder().decode(cookieValue);
            }

            final byte[] decryptedContent = cipherDecrypt.update(encryptedBytes); // seems like there should be a doFinal(), but route.coffee algorithm isn't calling it
            decrypted = new String(decryptedContent, StandardCharsets.UTF_8);
            decrypted = zeroTrim(decrypted);
        } catch (UnsupportedEncodingException e) {
            logger.error("Problem when decrypt UserContext from cookie", e);
        }
        return decrypted;
    }
}
