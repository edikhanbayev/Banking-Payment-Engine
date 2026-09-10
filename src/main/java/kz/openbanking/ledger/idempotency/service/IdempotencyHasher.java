package kz.openbanking.ledger.idempotency.service;

import kz.openbanking.ledger.payment.domain.InternalTransferCommand;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class IdempotencyHasher {

    public String hash(
            InternalTransferCommand command
    ) {

        String canonicalRequest =
                "POST\n" +
                        "/api/v1/transfers/internal\n" +
                        "from=" + command.fromAccountId() + "\n" +
                        "to=" + command.toAccountId() + "\n" +
                        "amountMinor=" + command.amountMinor() + "\n" +
                        "currency=" + command.currency();


        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");


            byte[] hashed =
                    digest.digest(
                            canonicalRequest.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );


            return HexFormat
                    .of()
                    .formatHex(hashed);

        }
        catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }
}
