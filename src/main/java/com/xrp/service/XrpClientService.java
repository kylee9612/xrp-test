package com.xrp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;
import com.xrp.util.XrpRequestParamUtil;
import okhttp3.HttpUrl;
import org.bouncycastle.crypto.util.PublicKeyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.client.JsonRpcRequest;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.client.faucet.FaucetClient;
import org.xrpl.xrpl4j.client.faucet.FundAccountRequest;
import org.xrpl.xrpl4j.crypto.KeyMetadata;
import org.xrpl.xrpl4j.crypto.PrivateKey;
import org.xrpl.xrpl4j.crypto.PublicKey;
import org.xrpl.xrpl4j.crypto.signing.SignatureService;
import org.xrpl.xrpl4j.crypto.signing.SignedTransaction;
import org.xrpl.xrpl4j.crypto.signing.SingleKeySignatureService;
import org.xrpl.xrpl4j.model.client.XrplRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoResult;
import org.xrpl.xrpl4j.model.client.accounts.AccountTransactionsResult;
import org.xrpl.xrpl4j.model.client.common.LedgerIndex;
import org.xrpl.xrpl4j.model.client.common.LedgerSpecifier;
import org.xrpl.xrpl4j.model.client.fees.FeeResult;
import org.xrpl.xrpl4j.model.client.ledger.LedgerRequestParams;
import org.xrpl.xrpl4j.model.client.serverinfo.ServerInfoResult;
import org.xrpl.xrpl4j.model.client.transactions.SubmitResult;
import org.xrpl.xrpl4j.model.client.transactions.TransactionRequestParams;
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult;
import org.xrpl.xrpl4j.model.immutables.FluentCompareTo;
import org.xrpl.xrpl4j.model.transactions.*;
import org.xrpl.xrpl4j.wallet.Wallet;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
public class XrpClientService {
    private static final Logger log = LoggerFactory.getLogger(XrpClientService.class);


    @Value("${xrp.url}")
    private String url;
    @Value("${xrp.test}")
    private boolean isTest;

    private XrplClient xrplClient;
    private FaucetClient faucetClient;
    private XrpRequestParamUtil paramUtil;

    @PostConstruct
    private void init(){
        System.out.println(url);
        xrplClient = new XrplClient(HttpUrl.get(url));
        faucetClient = FaucetClient.construct(HttpUrl.get("https://faucet.altnet.rippletest.net"));
        paramUtil = new XrpRequestParamUtil();
    }

    public String checkServer() {
        ServerInfoResult result = null;
        try {
            result = xrplClient.serverInformation();
            log.info(result.toString());
            return result.toString();
        } catch (JsonRpcClientErrorException e) {
            log.error(e.getMessage());
            return null;
        }
    }


    public Map<String, Object> getInfo() {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("Server Info", xrplClient.serverInformation());
            map.put("Node Fee", xrplClient.fee());
            map.put("JsonRpcClient", xrplClient.getJsonRpcClient());
            log.info(map + "");
            return map;
        } catch (JsonRpcClientErrorException e) {
            log.error(e.getMessage());
        }
        return null;
    }

    public void fundFaucet(Address classicAddress) {
        if (isTest) {
            faucetClient.fundAccount(FundAccountRequest.of(classicAddress));
            System.out.println("Funded the account using the Testnet faucet.");
            try {
                Thread.sleep(1000 * 4);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        } else {
            log.error("Faucet is not valid on live server");
        }
    }

    public String checkBalance(Address classicAddress) throws JsonRpcClientErrorException {
        AccountInfoRequestParams requestParams = paramUtil.getAccountInfoRequest(classicAddress);
        AccountInfoResult accountInfoResult = getAccountInfo(requestParams);
        XrpCurrencyAmount balance = accountInfoResult.accountData().balance();
        log.info("Balance : " + balance.toXrp());
        return accountInfoResult.accountData().balance().toXrp().toString();
    }

    public AccountInfoResult getAccountInfo(AccountInfoRequestParams resultParams) throws JsonRpcClientErrorException {
        AccountInfoResult result = xrplClient.accountInfo(resultParams);
        log.info(result.toString());
        return result;
    }

    public AccountTransactionsResult accountTransactionsResult(Address address) {
        try {
            AccountTransactionsResult result = xrplClient.accountTransactions(address);
            log.info(result.toString());
            return result;
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public SetRegularKey getRegularKey(Wallet wallet) {
        SetRegularKey setRegularKey = null;
        try {
            setRegularKey = SetRegularKey
                    .builder()
                    .signingPublicKey(wallet.publicKey())
                    .account(wallet.classicAddress())
                    .fee(xrplClient.fee().drops().baseFee())
                    .build();
            log.info(setRegularKey.toString());
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return setRegularKey;
    }

    public FeeResult getFee() throws JsonRpcClientErrorException {
        return xrplClient.fee();
    }

    public void sendXRP(Wallet testWallet, String addressTo,BigDecimal bigAmount) throws JsonRpcClientErrorException, JsonProcessingException, InterruptedException {
        Address classicAddress = testWallet.classicAddress();

        AccountInfoRequestParams requestParams = paramUtil.getAccountInfoRequest(classicAddress);
        AccountInfoResult accountInfoResult = getAccountInfo(requestParams);
        final UnsignedInteger sequence = accountInfoResult.accountData().sequence();

        final FeeResult feeResult = xrplClient.fee();
        final XrpCurrencyAmount openLedgerFee = feeResult.drops().openLedgerFee();

        // Construct a Payment
        final LedgerIndex validatedLedger = xrplClient.ledger(LedgerRequestParams.builder().ledgerSpecifier(LedgerSpecifier.VALIDATED).build())
                .ledgerIndex()
                .orElseThrow(() -> new RuntimeException("LedgerIndex not available."));

        final UnsignedInteger lastLedgerSequence = UnsignedInteger.valueOf(
                validatedLedger.plus(UnsignedInteger.valueOf(6)).unsignedIntegerValue().intValue()
        ); // <-- LastLedgerSequence is the current ledger index + 4

        Payment payment = Payment.builder()
                .account(classicAddress)
                .amount(XrpCurrencyAmount.ofXrp(bigAmount))
                .destination(Address.of(addressTo))
                .sequence(sequence)
                .fee(openLedgerFee)
                .signingPublicKey(testWallet.publicKey())
                .lastLedgerSequence(lastLedgerSequence)
                .build();
        log.info("Constructed Payment: " + payment);

        // Construct a SignatureService to sign the Payment
        PrivateKey privateKey = PrivateKey.fromBase16EncodedPrivateKey(testWallet.privateKey().get());
        SignatureService signatureService = new SingleKeySignatureService(privateKey);

        // Sign the Payment
        final SignedTransaction<Payment> signedPayment = signatureService.sign(KeyMetadata.EMPTY, payment);
        System.out.println("Signed Payment: " + signedPayment.signedTransaction());

        // Submit the Payment
        final SubmitResult<Transaction> submitResult = xrplClient.submit(signedPayment);
        System.out.println(submitResult);

        // Wait for validation
        TransactionResult<Payment> transactionResult = null;

        boolean transactionValidated = false;
        boolean transactionExpired = false;
        while (!transactionValidated && !transactionExpired) {
            Thread.sleep(4 * 1000);
            final LedgerIndex latestValidatedLedgerIndex = xrplClient.ledger(
                            LedgerRequestParams.builder().ledgerSpecifier(LedgerSpecifier.VALIDATED).build()
                    )
                    .ledgerIndex()
                    .orElseThrow(() -> new RuntimeException("Ledger response did not contain a LedgerIndex."));

            transactionResult = xrplClient.transaction(
                    TransactionRequestParams.of(signedPayment.hash()),
                    Payment.class
            );

            if (transactionResult.validated()) {
                System.out.println("Payment was validated with result code " + transactionResult.metadata().get().transactionResult());
                transactionValidated = true;
            } else {
                final boolean lastLedgerSequenceHasPassed = FluentCompareTo.
                        is(latestValidatedLedgerIndex.unsignedLongValue())
                        .greaterThan(UnsignedLong.valueOf(lastLedgerSequence.intValue()));
                if (lastLedgerSequenceHasPassed) {
                    System.out.println("LastLedgerSequence has passed. Last tx response: " +
                            transactionResult);
                    transactionExpired = true;
                } else {
                    System.out.println("Payment not yet validated.");
                }
            }
        }

        // Check transaction results
        System.out.println(transactionResult);
        System.out.println("Explorer link: https://testnet.xrpl.org/transactions/" + signedPayment.hash());
        transactionResult.metadata().ifPresent(metadata -> {
            System.out.println("Result code: " + metadata.transactionResult());

            metadata.deliveredAmount().ifPresent(deliveredAmount ->
                    System.out.println("XRP Delivered: " + ((XrpCurrencyAmount) deliveredAmount).toXrp())
            );
        });
    }
}
