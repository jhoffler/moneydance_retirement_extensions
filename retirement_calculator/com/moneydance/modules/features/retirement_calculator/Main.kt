package com.moneydance.modules.features.retirement_calculator

import com.moneydance.apps.md.controller.FeatureModule
import com.moneydance.apps.md.controller.FeatureModuleContext
import com.infinitekind.moneydance.model.*
import javax.swing.JOptionPane

class Main : FeatureModule() {
    private var calculatorWindow: CalculatorWindow? = null

    override fun init() {
        val bookContext = context
        try {
            bookContext?.registerFeature(this, "show_calculator", null, "Retirement Calculator")
        } catch (e: Exception) {
            e.printStackTrace(System.err)
        }
    }

    override fun invoke(uri: String) {
        if (uri.startsWith("show_calculator")) {
            showCalculator()
        }
    }

    override fun cleanup() {
        closeConsole()
    }

    override fun getName(): String {
        return "Retirement Calculator"
    }

    @Synchronized
    private fun showCalculator() {
        val bookContext = context
        bookContext?.showURL("moneydance:setprogress?label=Opening Retirement Calculator...")
        
        try {
            if (calculatorWindow == null) {
                val book = bookContext?.currentAccountBook
                if (book == null) {
                    JOptionPane.showMessageDialog(null, "No Moneydance dataset is currently open.", "Error", JOptionPane.ERROR_MESSAGE)
                    bookContext?.showURL("moneydance:setprogress?label=")
                    return
                }
                calculatorWindow = CalculatorWindow(this, book)
                calculatorWindow?.isVisible = true
            } else {
                calculatorWindow?.isVisible = true
                calculatorWindow?.toFront()
                calculatorWindow?.requestFocus()
                bookContext?.showURL("moneydance:setprogress?label=")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            bookContext?.showURL("moneydance:setprogress?label=")
        }
    }

    @Synchronized
    fun closeConsole() {
        calculatorWindow?.isVisible = false
        calculatorWindow?.dispose()
        calculatorWindow = null
        System.gc()
    }

    fun getUnprotectedContext(): FeatureModuleContext? {
        return context
    }
}

class AcctTypeFilter : AcctFilter() {
    private val myTypes = listOf(Account.AccountType.BANK, Account.AccountType.INVESTMENT)
    
    override fun format(acct: Account): String? {
        return acct.getAccountName()
    }
    
    override fun matches(acct: Account?): Boolean {
        if (acct == null) return false
        val acctType = acct.getAccountType()
        return acctType in myTypes && !acct.accountIsInactive
    }
}

fun getRecursiveBalanceAsOfDate(book: AccountBook, acct: Account, dateInt: Int): Long {
    val rawBal = AccountUtil.getBalanceAsOfDate(book, acct, dateInt)
    var bal = if (acct.getAccountType() == Account.AccountType.SECURITY) {
        val parent = acct.getParentAccount()
        val parentCurrency = parent?.currencyType ?: book.currencies.baseType
        CurrencyUtil.convertValue(rawBal, acct.currencyType, parentCurrency, dateInt)
    } else {
        rawBal
    }
    
    val subAccounts = acct.getSubAccounts()
    if (subAccounts != null) {
        for (subAcct in subAccounts) {
            bal += getRecursiveBalanceAsOfDate(book, subAcct, dateInt)
        }
    }
    return bal
}

fun getHistoricalSecurityCostBasis(book: AccountBook, subAcct: Account, dateInt: Int): Long {
    val actualShares = AccountUtil.getBalanceAsOfDate(book, subAcct, dateInt)
    if (actualShares == 0L) return 0L
    
    val today = java.time.LocalDate.now()
    val todayDateInt = today.year * 10000 + today.monthValue * 100 + today.dayOfMonth
    
    if (dateInt >= todayDateInt) {
        return InvestUtil.getCostBasis(subAcct)
    }
    
    try {
        val costCalc = InvestUtil.getCostCalculation(subAcct)
        costCalc.asOfDate = dateInt
        
        val sharesAndCostM = costCalc.javaClass.getMethod("getSharesAndCostBasisForAsOf")
        val sharesAndCost = sharesAndCostM.invoke(costCalc)
        if (sharesAndCost != null) {
            val scClass = sharesAndCost.javaClass
            val getSharesOwnedM = scClass.getMethod("getSharesOwnedAsOf")
            val getCostBasisAsOfM = scClass.getMethod("getCostBasisAsOf")
            
            val costCalcShares = getSharesOwnedM.invoke(sharesAndCost) as Long
            val costCalcCost = getCostBasisAsOfM.invoke(sharesAndCost) as Long
            
            if (costCalcShares <= 0L) {
                return 0L
            }
            val result = (costCalcCost.toDouble() * (actualShares.toDouble() / costCalcShares.toDouble())).toLong()
            return result
        }
    } catch (e: Exception) {
        return InvestUtil.getCostBasis(subAcct, dateInt)
    }
    return 0L
}

class AccountDivider(private val book: AccountBook, private val dateInt: Int) {
    val roths = mutableListOf<Account>()
    val iras = mutableListOf<Account>()
    val taxables = mutableListOf<Account>()
    
    fun add(acct: Account) {
        if (getRecursiveBalanceAsOfDate(book, acct, dateInt) == 0L) return
        
        val nameLower = (acct.getAccountName() ?: "").lowercase()
        if (nameLower.contains("christopher") || nameLower.contains("nikki") || nameLower.contains("dafgiving")) {
            return
        }
        
        if (nameLower.contains("roth") || nameLower.contains("hsa")) {
            roths.add(acct)
        } else if (listOf("ira", "pension", "retirement", "401", "457", "tiaa").any { nameLower.contains(it) }) {
            iras.add(acct)
        } else {
            taxables.add(acct)
        }
    }
}
