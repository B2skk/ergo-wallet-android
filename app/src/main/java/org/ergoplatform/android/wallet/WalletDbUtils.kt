package org.ergoplatform.android.wallet

/**
 * sums up all balances of derived address
 */
fun WalletDbEntity.getBalanceForAllAddresses(): Long {
    return state.map { it.balance ?: 0 }.sum()
}

/**
 * sums up all unconfirmed balances of derived address
 */
fun WalletDbEntity.getUnconfirmedBalanceForAllAddresses(): Long {
    return state.map { it.unconfirmedBalance ?: 0 }.sum()
}

fun WalletDbEntity.getTokensForAllAddresses(): List<WalletTokenDbEntity> {
    // combine tokens with same id but different list entries
    val hashmap = HashMap<String, WalletTokenDbEntity>()

    tokens.forEach {
        hashmap.put(it.tokenId!!, hashmap.get(it.tokenId)?.let { tokenInMap ->
            WalletTokenDbEntity(
                0, "", it.walletFirstAddress, it.tokenId,
                (it.amount ?: 0) + (tokenInMap.amount ?: 0), it.decimals, it.name
            )
        } ?: it)
    }

    val combinedTokens = hashmap.values
    return if (combinedTokens.size < tokens.size) combinedTokens.toList() else tokens
}

fun WalletDbEntity.getTokensForAddress(address: String): List<WalletTokenDbEntity> {
    return tokens.filter { it.publicAddress.equals(address) }
}

/**
 * @return derived address string with given index
 */
fun WalletDbEntity.getDerivedAddress(derivationIdx: Int): String? {
    // edge case: index 0 is not (always) part of addresses table
    if (derivationIdx == 0) {
        return walletConfig.firstAddress
    } else {
        return addresses.firstOrNull { it.derivationIndex == derivationIdx }?.publicAddress
    }
}

/**
 * @return derived address entity with given derivation index
 */
fun WalletDbEntity.getDerivedAddressEntity(derivationIdx: Int): WalletAddressDbEntity? {
    val allAddresses = ensureWalletAddressListHasFirstAddress(addresses, walletConfig.firstAddress!!)
    return allAddresses.firstOrNull { it.derivationIndex == derivationIdx }
}

/**
 * @return derived addresses list, making sure that 0 address is included and list is sorted by idx
 */
fun WalletDbEntity.getSortedDerivedAddressesList(): List<WalletAddressDbEntity> {
    val retList = ensureWalletAddressListHasFirstAddress(addresses, walletConfig.firstAddress!!)
    return retList.sortedBy { it.derivationIndex }
}

fun ensureWalletAddressListHasFirstAddress(
    addresses: List<WalletAddressDbEntity>,
    firstAddress: String
): List<WalletAddressDbEntity> {
    val hasFirst = addresses.filter { it.derivationIndex == 0 }.isNotEmpty()
    val retList = addresses.toMutableList()

    if (!hasFirst) {
        retList.add(
            0,
            WalletAddressDbEntity(
                0,
                firstAddress,
                0,
                firstAddress,
                null
            )
        )
    }
    return retList
}

fun WalletDbEntity.getNumOfAddresses(): Int {
    // edge case: index 0 is not (always) part of addresses table
    return addresses.filter { it.derivationIndex != 0 }.size + 1
}

fun WalletDbEntity.getStateForAddress(address: String): WalletStateDbEntity? {
    return state.filter { it.publicAddress.equals(address) }.firstOrNull()
}