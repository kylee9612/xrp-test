package com.xrp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xrpl.xrpl4j.keypairs.KeyPair;
import org.xrpl.xrpl4j.model.client.accounts.AccountInfoRequestParams;
import org.xrpl.xrpl4j.model.client.common.LedgerIndex;
import org.xrpl.xrpl4j.model.transactions.Address;
import org.xrpl.xrpl4j.wallet.DefaultWalletFactory;
import org.xrpl.xrpl4j.wallet.Wallet;
import org.xrpl.xrpl4j.wallet.WalletFactory;

@Service
public class XrpWalletService {

    @Value("${xrp.test}")
    private boolean isTest;
    private final WalletFactory walletFactory = DefaultWalletFactory.getInstance();

    public Wallet generateWallet(){
        return walletFactory.randomWallet(isTest).wallet();
    }
    public KeyPair getKeyPair(String publicKey, String privateKey){
        return KeyPair.builder().
                publicKey(publicKey).
                privateKey(privateKey).
                build();
    }
    public Wallet getWallet(String publicKey, String privateKey){
        KeyPair key = getKeyPair(publicKey, privateKey);
        return walletFactory.fromKeyPair(key,isTest);
    }
    public Wallet getWallet(String secretKey) {
        return walletFactory.fromSeed(secretKey, isTest);
    }
    public void walletInfo(Wallet wallet) {
        System.out.println("Public key : " + wallet.publicKey());
        System.out.println("Private key : " + wallet.privateKey().get());
        System.out.println("Classic Address : " + wallet.classicAddress());
        System.out.println("xAddress : " + wallet.xAddress());
    }

    public AccountInfoRequestParams getAccountInfoRequest(Address classicAddress) {
        return AccountInfoRequestParams
                .builder().ledgerIndex(LedgerIndex.VALIDATED)
                .account(classicAddress)
                .build();
    }
}
