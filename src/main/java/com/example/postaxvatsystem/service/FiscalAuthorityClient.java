package com.example.postaxvatsystem.service;

import com.example.postaxvatsystem.dto.FiscalReceiptResult;
import com.example.postaxvatsystem.model.POSTransaction;

public interface FiscalAuthorityClient {
    FiscalReceiptResult submitReceipt(POSTransaction transaction, boolean creditNote);
}
