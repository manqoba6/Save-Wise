package com.savewise.dto;

/** Result of a deposit or withdrawal: the recorded transaction plus the account's new balance. */
public record SavingsOperationResponse(SavingTransactionResponse transaction, SavingAccountResponse account) {
}
