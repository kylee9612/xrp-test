package com.axia.xrp.task;

import com.axia.common.task.AbstractDemonTask;
import com.axia.xrp.service.XrpClientService;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;
import okhttp3.HttpUrl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xrpl.xrpl4j.client.JsonRpcClientErrorException;
import org.xrpl.xrpl4j.client.XrplClient;
import org.xrpl.xrpl4j.model.client.common.LedgerIndex;
import org.xrpl.xrpl4j.model.client.common.LedgerSpecifier;
import org.xrpl.xrpl4j.model.client.ledger.LedgerRequestParams;
import org.xrpl.xrpl4j.model.client.ledger.LedgerResult;
import org.xrpl.xrpl4j.model.client.transactions.TransactionRequestParams;
import org.xrpl.xrpl4j.model.client.transactions.TransactionResult;
import org.xrpl.xrpl4j.model.ledger.LedgerHeader;
import org.xrpl.xrpl4j.model.transactions.OfferCreate;
import org.xrpl.xrpl4j.model.transactions.Payment;
import org.xrpl.xrpl4j.model.transactions.Transaction;

import javax.annotation.PostConstruct;
import java.util.List;

public class XrpReceiveTask extends AbstractDemonTask {

    private final static Logger log = LogManager.getLogger(XrpReceiveTask.class);

    private static UnsignedInteger lastLedgerIndex = UnsignedInteger.valueOf(0);

    private XrplClient xrplClient;


    private final String clientAddress;

    public XrpReceiveTask(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    @Override
    protected void execute() throws Exception {
        openDBNode();
        while (true) {
            LedgerResult result = xrplClient.ledger(
                    LedgerRequestParams.builder()
                            .ledgerSpecifier(LedgerSpecifier.VALIDATED)
                            .transactions(true)
                            .build());
            UnsignedInteger ledgerIndex = result.ledgerIndex().get().unsignedIntegerValue();
            lastLedgerIndex = lastLedgerIndex.equals(UnsignedInteger.ZERO) ? ledgerIndex : lastLedgerIndex;
            if (ledgerIndex.compareTo(lastLedgerIndex) == 1) {
                lastLedgerIndex = ledgerIndex;
                log.info("Ledger Index : " + result.ledger().ledgerIndex());

                LedgerHeader header = result.ledger();
                List<TransactionResult<? extends Transaction>> transactions = header.transactions();
                for (TransactionResult tr : transactions) {
                    if (tr.transaction() instanceof Payment payment) {
                        log.info(payment);
                        log.info("Tx Hash : " + payment.hash());
                        log.info("Destination : " + payment.destination());
                        log.info("Account From : " + payment.account());
                        log.info("Amount :" + payment.amount());
                        log.info("Tag : " + payment.destinationTag());
                        log.info("Source Tag : " + payment.sourceTag());
                    }
                }
            }
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                return;
            }
        }
    }

    private LedgerResult getLedgerResult(LedgerIndex index) throws JsonRpcClientErrorException, InterruptedException {
        Thread.sleep(1000);
        return xrplClient.ledger(
                LedgerRequestParams
                        .builder()
                        .ledgerSpecifier(LedgerSpecifier.of(index))
                        .transactions(true)
                        .build());
    }

    @Override
    protected void openDBNode() {
        if (xrplClient == null)
            xrplClient = new XrplClient(HttpUrl.get(clientAddress));
    }

    @Override
    protected void closeDBNode() {
        if (xrplClient != null)
            xrplClient = null;
    }
}