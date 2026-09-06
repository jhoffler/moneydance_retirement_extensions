package com.moneydance.modules.features.retirement_calculator

import com.infinitekind.moneydance.model.Account
import com.infinitekind.moneydance.model.AccountBook
import com.infinitekind.moneydance.model.AccountUtil
import com.infinitekind.moneydance.model.InvestUtil
import com.infinitekind.moneydance.model.SplitTxn
import java.net.URLEncoder
import javax.swing.SwingUtilities

data class MoneydanceBalancesResult(
    val taxableLotsStr: String,
    val iraBal: Double,
    val iraCash: Double,
    val rothBal: Double,
    val rothCash: Double,
    val otherBal: Double,
    val otherCash: Double,
    val otherBasis: Double,
    val dafBal: Double,
    val taxableMmf: Double
)

object MoneydanceDataLoader {

    fun isMoneyMarketFund(subAcct: Account): Boolean {
        // 1. SecurityType override (CD)
        try {
            val secTypeM = subAcct.javaClass.getMethod("getSecurityType")
            val secType = secTypeM.invoke(subAcct)
            if (secType != null && secType.toString() == "CD") {
                return true
            }
        } catch (_: Throwable) {}

        // 2. Account Name matching
        val acctName = (subAcct.getAccountName() ?: "").lowercase()
        if (acctName.contains("money")) {
            return true
        }

        // 3. CurrencyType Name & Ticker matching
        try {
            val getCurrM = subAcct.javaClass.getMethod("getCurrencyType")
            val curr = getCurrM.invoke(subAcct)
            if (curr != null) {
                val getNameM = curr.javaClass.getMethod("getName")
                val currName = (getNameM.invoke(curr) as? String ?: "").lowercase()
                if (currName.contains("money")) {
                    return true
                }
                val getTickerM = curr.javaClass.getMethod("getTickerSymbol")
                val ticker = (getTickerM.invoke(curr) as? String ?: "").trim().uppercase()
                if (ticker.length >= 4 && ticker.endsWith("XX")) {
                    return true
                }
            }
        } catch (_: Throwable) {}

        return false
    }

    fun loadBalances(
        mdBook: AccountBook,
        dateInt: Int,
        onProgress: ((String) -> Unit)? = null,
        onSuccess: (MoneydanceBalancesResult) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        onProgress?.invoke("moneydance:setprogress?label=Loading retirement account balances...")
        Thread {
            try {
                if (dateInt > 0) {
                    val allAccounts = AccountUtil.allMatchesForSearch(mdBook, AcctTypeFilter())
                    val dafAccounts = mutableListOf<Account>()
                    val otherAccounts = mutableListOf<Account>()

                    if (allAccounts != null) {
                        for (acct in allAccounts) {
                            val nameLower = (acct.getAccountName() ?: "").lowercase()
                            if (nameLower.contains("daf") || nameLower.contains("donor advised") || nameLower.contains("charity")) {
                                dafAccounts.add(acct)
                            } else {
                                otherAccounts.add(acct)
                            }
                        }
                    }

                    val divider = AccountDivider(mdBook, dateInt)
                    for (acct in otherAccounts) {
                        divider.add(acct)
                    }

                    fun sumAccounts(grp: List<Account>): Long {
                        var sum = 0L
                        for (acct in grp) {
                            sum += getRecursiveBalanceAsOfDate(mdBook, acct, dateInt)
                        }
                        return sum
                    }

                    fun sumBasis(grp: List<Account>): Long {
                        var sum = 0L
                        for (acct in grp) {
                            if (acct.getAccountType() == Account.AccountType.INVESTMENT) {
                                var totalInvestmentCostBasis = 0L
                                val subAccounts = acct.getSubAccounts()
                                if (subAccounts != null) {
                                    for (subAcct in subAccounts) {
                                        if (subAcct.getAccountType() == Account.AccountType.SECURITY) {
                                            totalInvestmentCostBasis += getHistoricalSecurityCostBasis(mdBook, subAcct, dateInt)
                                        }
                                    }
                                }
                                sum += totalInvestmentCostBasis + AccountUtil.getBalanceAsOfDate(mdBook, acct, dateInt)
                            } else {
                                sum += AccountUtil.getBalanceAsOfDate(mdBook, acct, dateInt)
                            }
                        }
                        return sum
                    }

                    fun sumCash(grp: List<Account>): Long {
                        var sum = 0L
                        for (acct in grp) {
                            if (acct.getAccountType() == Account.AccountType.INVESTMENT) {
                                sum += AccountUtil.getBalanceAsOfDate(mdBook, acct, dateInt)
                                val subAccounts = acct.getSubAccounts()
                                if (subAccounts != null) {
                                    for (subAcct in subAccounts) {
                                        if (subAcct.getAccountType() == Account.AccountType.SECURITY && isMoneyMarketFund(subAcct)) {
                                            sum += getRecursiveBalanceAsOfDate(mdBook, subAcct, dateInt)
                                        }
                                    }
                                }
                            } else {
                                sum += getRecursiveBalanceAsOfDate(mdBook, acct, dateInt)
                            }
                        }
                        return sum
                    }

                    fun sumMmf(grp: List<Account>): Long {
                        var sum = 0L
                        for (acct in grp) {
                            if (acct.getAccountType() == Account.AccountType.INVESTMENT) {
                                val subAccounts = acct.getSubAccounts()
                                if (subAccounts != null) {
                                    for (subAcct in subAccounts) {
                                        if (subAcct.getAccountType() == Account.AccountType.SECURITY && isMoneyMarketFund(subAcct)) {
                                            sum += getRecursiveBalanceAsOfDate(mdBook, subAcct, dateInt)
                                        }
                                    }
                                }
                            }
                        }
                        return sum
                    }

                    val taxableLots = mutableListOf<String>()
                    for (acct in divider.taxables) {
                        if (acct.getAccountType() == Account.AccountType.INVESTMENT) {
                            val subAccounts = acct.getSubAccounts()
                            if (subAccounts != null) {
                                for (subAcct in subAccounts) {
                                    if (subAcct.getAccountType() == Account.AccountType.SECURITY) {
                                        if (isMoneyMarketFund(subAcct)) {
                                            continue
                                        }
                                        val lotsTable = InvestUtil.getRemainingLots(mdBook, subAcct, dateInt)
                                        if (lotsTable != null && !lotsTable.isEmpty()) {
                                            for (key in lotsTable.keys) {
                                                val txnId = key as? String ?: continue
                                                val tracker = lotsTable[txnId] ?: continue
                                                val remainingShares = try {
                                                    val field = tracker.javaClass.getDeclaredField("availableShares")
                                                    field.isAccessible = true
                                                    field.getLong(tracker)
                                                } catch (_: Exception) {
                                                    0L
                                                }
                                                if (remainingShares > 0L) {
                                                    val buySplit = try {
                                                        val txnSet = mdBook.javaClass.getMethod("getTransactionSet").invoke(mdBook)
                                                        val rawTxn = txnSet.javaClass.getMethod("getTxnByID", String::class.java).invoke(txnSet, txnId)
                                                        if (rawTxn != null) {
                                                            if (rawTxn.javaClass.name.contains("SplitTxn")) {
                                                                rawTxn
                                                            } else if (rawTxn.javaClass.name.contains("ParentTxn")) {
                                                                var foundSplit: Any? = null
                                                                val getSplitM = rawTxn.javaClass.getMethod("getSplit", Int::class.java)
                                                                val getSplitCountM = rawTxn.javaClass.getMethod("getSplitCount")
                                                                val splitCount = getSplitCountM.invoke(rawTxn) as Int
                                                                for (idx in 0 until splitCount) {
                                                                    val split = getSplitM.invoke(rawTxn, idx) ?: continue
                                                                    val splitAcct = split.javaClass.getMethod("getAccount").invoke(split)
                                                                    if (splitAcct == subAcct) {
                                                                        foundSplit = split
                                                                        break
                                                                    }
                                                                }
                                                                foundSplit
                                                            } else {
                                                                rawTxn
                                                            }
                                                        } else {
                                                            null
                                                        }
                                                    } catch (_: Exception) {
                                                        null
                                                    }
                                                    if (buySplit != null) {
                                                        val originalShares = try {
                                                            buySplit.javaClass.getMethod("getValue").invoke(buySplit) as Long
                                                        } catch (_: Exception) {
                                                            0L
                                                        }
                                                        var originalCostBasis = try {
                                                            val amt = buySplit.javaClass.getMethod("getAmount").invoke(buySplit) as Long
                                                            Math.abs(amt)
                                                        } catch (_: Exception) {
                                                            0L
                                                        }
                                                        if (originalCostBasis == 0L) {
                                                            originalCostBasis = try {
                                                                val method = Class.forName("com.infinitekind.moneydance.model.InvestUtil")
                                                                    .getDeclaredMethod("getCostBasis", Account::class.java, SplitTxn::class.java)
                                                                method.isAccessible = true
                                                                method.invoke(null, subAcct, buySplit) as Long
                                                            } catch (_: Exception) {
                                                                0L
                                                            }
                                                        }

                                                        // Calculate remaining cost basis
                                                        val remainingCostBasis = if (originalShares > 0L) {
                                                            originalCostBasis.toDouble() * (remainingShares.toDouble() / originalShares.toDouble())
                                                        } else {
                                                            originalCostBasis.toDouble()
                                                        }

                                                        // Get current value of remaining shares
                                                        val totalShares = AccountUtil.getBalanceAsOfDate(mdBook, subAcct, dateInt)
                                                        val secVal = getRecursiveBalanceAsOfDate(mdBook, subAcct, dateInt)

                                                        val currentVal = if (totalShares > 0L) {
                                                            secVal.toDouble() * (remainingShares.toDouble() / totalShares.toDouble())
                                                        } else {
                                                            0.0
                                                        }

                                                        // Format name: Name (Bought YYYY-MM-DD)
                                                        val rawName = subAcct.getAccountName() ?: "Stock"
                                                        val buyDate = try {
                                                            buySplit.javaClass.getMethod("getDateInt").invoke(buySplit) as Int
                                                        } catch (_: Exception) {
                                                            0
                                                        }
                                                        val yr = buyDate / 10000
                                                        val mo = (buyDate % 10000) / 100
                                                        val dy = buyDate % 100
                                                        val dateStr = String.format("%04d-%02d-%02d", yr, mo, dy)
                                                        val lotName = "$rawName (Bought $dateStr)"

                                                        val encName = URLEncoder.encode(lotName, "UTF-8")
                                                        taxableLots.add("$encName,${remainingCostBasis / 100.0},${currentVal / 100.0}")
                                                    }
                                                }
                                            }
                                        } else {
                                            val secVal = getRecursiveBalanceAsOfDate(mdBook, subAcct, dateInt)
                                            val secBasis = getHistoricalSecurityCostBasis(mdBook, subAcct, dateInt)
                                            if (secVal > 0L) {
                                                val rawName = subAcct.getAccountName() ?: "Stock"
                                                val encName = URLEncoder.encode(rawName, "UTF-8")
                                                taxableLots.add("$encName,${secBasis / 100.0},${secVal / 100.0}")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val lotsStr = taxableLots.joinToString(";")

                    val iraBal = sumAccounts(divider.iras) / 100.0
                    val iraCash = sumCash(divider.iras) / 100.0
                    val rothBal = sumAccounts(divider.roths) / 100.0
                    val rothCash = sumCash(divider.roths) / 100.0
                    val otherBal = sumAccounts(divider.taxables) / 100.0
                    val otherCash = sumCash(divider.taxables) / 100.0
                    val otherBasis = sumBasis(divider.taxables) / 100.0
                    val dafBal = sumAccounts(dafAccounts) / 100.0
                    val taxableMmf = sumMmf(divider.taxables) / 100.0

                    val result = MoneydanceBalancesResult(
                        taxableLotsStr = lotsStr,
                        iraBal = iraBal,
                        iraCash = iraCash,
                        rothBal = rothBal,
                        rothCash = rothCash,
                        otherBal = otherBal,
                        otherCash = otherCash,
                        otherBasis = otherBasis,
                        dafBal = dafBal,
                        taxableMmf = taxableMmf
                    )

                    SwingUtilities.invokeLater {
                        onSuccess(result)
                        onProgress?.invoke("moneydance:setprogress?label=")
                    }
                } else {
                    SwingUtilities.invokeLater {
                        onProgress?.invoke("moneydance:setprogress?label=")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onError?.invoke(e)
                SwingUtilities.invokeLater {
                    onProgress?.invoke("moneydance:setprogress?label=")
                }
            }
        }.start()
    }
}
