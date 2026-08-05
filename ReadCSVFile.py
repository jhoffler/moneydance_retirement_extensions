#!/usr/bin/env python
# Python script to be run in Moneydance to perform amazing feats of financial scripting

from com.infinitekind.moneydance.model import *

import sys
import time

acctName = "John Roth - Edw Jones"
katy_ira_file = "C:\\Users\\jhoff\\Downloads\\activity_378770.csv"
john_ira_file = "C:\\Users\\jhoff\\Downloads\\activity_134558990.csv"
john_roth_file = "C:\\Users\\jhoff\\Downloads\\activity_100127788.csv"
file = john_roth_file


class FIELD:
    DATE = 0
    ACTIVITY = 1
    DESCRIPTION = 2
    QUANTITY = 3
    PRICE = 4
    AMOUNT = 5
    ACCOUNT = 6

accountNames = {
    "John IRA": "John IRA - Edw Jones",
    "Katy IRA": "Katy IRA - Edw Jones",
    "John Roth IRA": "John Roth - Edw Jones"
}


def translateDate(date):
    parts = date.split("/")
    if len(parts) < 3:
        print("%s doesn't have 3 parts"%(date))
    return int(parts[2]) * 10000 + int(parts[0]) * 100 + int(parts[1])

def printTxn(txn, idx):
    print "%d) %d - %s:"%(idx, txn.getDateInt(), txn.getInvestTxnType())
    print "\tAccount: %s"%(txn.getAccount().getAccountName())
    print "\tDesc: %s"%(txn.getDescription())
    print "\tMemo: %s"%(txn.getMemo())
    print "\tValue: %s"%(txn.getValue())
    print "\tStatus: %s"%(txn.getStatus())

    print "\tSplits: %d"%(txn.getSplitCount())
    for s in range(txn.getSplitCount()):
        print "\t\t%d) Desc: %s"%(s+1, txn.getSplit(s).getDescription())
        print "\t\t%d) Type: %s"%(s+1, txn.getSplit(s).getTransferType())
        print "\t\t%d) Account: %s"%(s+1, txn.getSplit(s).getAccount().getAccountName())
        print "\t\t%d) Value: %s"%(s+1, txn.getSplit(s).getValue())
        print "\t\t%d) Amt: %s"%(s+1, txn.getSplit(s).getAmount())
        print "\t\t%d) Rate: %s"%(s+1, txn.getSplit(s).getRate())

def getDescription(fields):
    txn_type = fields[FIELD.ACTIVITY]
    desc = fields[FIELD.DESCRIPTION]
    if txn_type == "Dividend":
        idx = desc.index("CASH DIV")
        return desc[idx:], desc[idx:]
    elif txn_type == "Liquidation":
        idx = desc.index(" ON ")
        return desc[idx+1:], desc[idx+1:]
    elif txn_type == "Reinvestment into":
        idx = desc.index("FROM")
        return desc[idx:], desc[idx:]
    elif txn_type == "Program and Portf Strat Fees" or txn_type == "Program Fee":
        return txn_type, txn_type
    else:
        return "", ""

def getTxnType(fields):
    txn_type = fields[FIELD.ACTIVITY]
    if txn_type == "Dividend":
        return InvestTxnType.DIVIDEND
    elif txn_type == "Liquidation" or txn_type == "Sell":
        return InvestTxnType.SELL
    elif txn_type == "Reinvestment into" or txn_type == "Buy":
        return InvestTxnType.BUY
    elif txn_type == "Program and Portf Strat Fees" or txn_type == "Program Fee":
        return InvestTxnType.MISCEXP

def getSplitAcct(fields):
    # portfolio fees are Invest Exp
    if fields[FIELD.ACTIVITY] == "Program and Portf Strat Fees" or fields[FIELD.ACTIVITY] == "Program Fee":
        return "Invest Exp"
    desc = fields[FIELD.DESCRIPTION].lstrip("*")
    for key in [" FROM ", " CASH DIV", " PROSPECTUS", " AS OF ", " ON ", " from "]:
        idx = desc.find(key)
        if idx > -1:
            fund = desc[:idx]
            return fund
    return desc


class AcctNameFilter(AcctFilter):
    def __init__(self, acctName):
        nameMap = {
            "BOND FD AMER CL F 3": "AMERICAN BOND FUND OF AMERICA  CL F3",
            "BOND FD AMER CL F 3 SOLICITED": "AMERICAN BOND FUND OF AMERICA  CL F3",
            "CAPITAL WORLD BD FD CL F 3 SOLICITED": "AMERICAN CAPITAL WORLD BOND CL F3",
            "GROWTH FD AMER CL F 3 SOLICITED": "AMERICAN GROWTH FUND OF AMERICA CL F3",
            "HARTFORD MUT FDS INC DIVIDEND & GROWTH FD CL F SOLICITED": "HARTFORD DIVIDEND & GROWTH CL  F",
            "HARTFORD MUT FDS INC EQUITY INCOME FD CL F SOLICITED": "HARTFORD EQUITY INCOME CL F",
            "HARTFORD MUT FDS INC INTERNATIONAL OPPTYS FD CL F SOLICITED": "HARTFORD INTERNATIONAL OPPORTUNITIES CL F",
            "HARTFORD MUT FDS INC MIDCAP FD CL F SOLICITED": "HARTFORD MIDCAP CL F",
            "HARTFORD MUT FDS INC SMALL CO FD CL F SOLICITED": "HARTFORD SMALL COMPANY CL F",
            "HARTFORD MUT FDS INC TOTAL RETURN BD FD CL F": "HARTFORD TOTAL RETURN BOND CL  F",
            "HARTFORD MUT FDS INC TOTAL RETURN BD FD CL F SOLICITED": "HARTFORD TOTAL RETURN BOND CL  F",
            "JOHN HANCOCK FDS II INTL SMALL CO FD CL R6 SOLICITED": "JOHN HANCOCK INTERNATIONAL SMALL COMPANY CL R6",
            "MFS SER TR I VALUE FD CL R6 SOLICITED": "MFS VALUE CL R6",
            "ROWE T PRICE U S TREAS FDS INC U S TREAS MONEY FD CL I": "T ROWE PRICE U.S. TREASURY MONEY CL I",
            "ROWE T PRICE U S TREAS FDS INC U S TREAS MONEY FD CL I SOLICITED": "T ROWE PRICE U.S. TREASURY MONEY CL I",
            "BRIDGE BUILDER INTERNATIONAL EQUITY SOLICITED": "BRIDGE BUILDER INTL EQUITY",
            "BRIDGE BUILDER LARGE VALUE FD": "BRIDGE BUILDER LARGE VALUE",
            "BRIDGE BUILDER CORE PLUS BOND SOLICITED": "BRIDGE BUILDER CORE PLUS BOND",
            "BRIDGE BUILDER TR CORE BOND FD": "BRIDGE BUILDER CORE BOND",
            "DFA INTERNATIONAL VALUE PORTFOLIO SOLICITED": "DFA INTERNATIONAL VALUE CL I",
            "INTERNATIONAL GROWTH & INCOME FD CL F 3 SHS": "AMERICAN INTERNATIONAL GROWTH  & INCOME CL F3",
            "INTERNATIONAL GROWTH & INCOME FD CL F 3 SHS SOLICITED": "AMERICAN INTERNATIONAL GROWTH  & INCOME CL F3",
            "ISHARES S&P SMALL CAP 600 VALUE ETF": "ISHARES TR S&P SMALLCAP 600 VALUE FUND",
            "ISHARES S&P SMALL CAP 600 VALUE ETF SOLICITED": "ISHARES TR S&P SMALLCAP 600 VALUE FUND",
            "ISHARES RUSSELL 2000 ETF": "ISHARES RUSSELL 2000 FUND",
            "JPMORGAN TR II U S GOVT MONEY MKT FD CAP CL": "JPMORGAN U.S. GOVERNMENT MONEY MARKET CL CAPITAL",
            "JPMORGAN TR II U S GOVT MONEY MKT FD CAP CL SOLICITED": "JPMORGAN U.S. GOVERNMENT MONEY MARKET CL CAPITAL",
            "PIMCO HIGH YIELD FUND INSTL CL": "PIMCO HIGH YIELD CL I",
            "VANGUARD INDEX FUNDS VANGUARD MID-CAP ETF SOLICITED": "VANGUARD MID CAP ETF",
            "VANGUARD INDEX FUNDS VANGUARD VALUE ETF": "VANGUARD INDEX TR VANGUARD VALUE ETF",
            "VANGUARD INTL EQUITY INDEX FD INC FTSE ALL WORLD EX USA SMALL CAP ETF SOLICITED": "VANGUARD FTSE ALL-WORLD EX-US  SMALL-CAP INDEX ETF",
        }
        if acctName in nameMap:
            self.acctName = nameMap[acctName]
        elif acctName is None:
            self.acctName = acctName
        else:
            self.acctName = acctName.replace(" TR ", " ")

    def format(self, acct):
        return acct.getAccountName()
    
    def matches(self, aNacct):
        name = aNacct.getAccountName().encode('UTF-8')
        return name == self.acctName

# get the default environment variables, set by Moneydance
print "=================================="
print "The Moneydance app controller: %s"%(moneydance)
print "The current data set: %s"%(moneydance_data)
print "The UI: %s"%(moneydance_ui)
acct =  moneydance_data.getRootAccount().getAccountByName(acctName, Account.AccountType.INVESTMENT)
print "The %s account has %d tansactions"%(acct.getAccountName(), acct.getTxnCount())
txns = moneydance_data.getTransactionSet().getTransactionsForAccount(acct)
txns.sortByField(AccountUtil.DATE)
print "TransactionSet contains %d transactions"%(txns.getSize())
for i in range(6,1,-1):
    txn = txns.getTxn(txns.getSize() - i)
    printTxn(txn, i)
	

# Read transaction file
f = open(file, "r")
next(f)
counter = 0
for line in f:
    counter += 1
    fields = line.rstrip().split(",")
    new_txn = ParentTxn(moneydance_data)
    new_txn.setDateInt(translateDate(fields[FIELD.DATE]))
    new_txn.setTaxDateInt(translateDate(fields[FIELD.DATE]))
    txnAcctName = accountNames[fields[FIELD.ACCOUNT]]
    txnAcct = moneydance_data.getRootAccount().getAccountByName(txnAcctName, Account.AccountType.INVESTMENT)
#    for subAcct in txnAcct.getSubAccounts():
#        print subAcct.getAccountName()
    new_txn.setAccount(txnAcct)
    new_txn.setInvestTxnType(getTxnType(fields))
    desc, memo = getDescription(fields)
    new_txn.setDescription(desc)
    new_txn.setMemo(memo)
    # Create splits
    splitAcctName = getSplitAcct(fields)
    splitAccts =  txnAcct.getSubAccounts(AcctNameFilter(splitAcctName))
    if len(splitAccts) == 0:
        splitAcct = moneydance_data.getRootAccount().getAccountByName(splitAcctName)
    else:
        splitAcct = splitAccts[0]
    if splitAcct == None:
        print "Couldn't find %s"%(splitAcctName)
        continue
    else:
        print counter, splitAcct
    parentAmt = int(float(fields[FIELD.AMOUNT].replace("$", "")) * 100)
    splitAmt = int(float(fields[FIELD.QUANTITY].replace("$", "")) * 100)
    splitRate = float(fields[FIELD.PRICE].lstrip("$"))
    if getTxnType(fields) == InvestTxnType.DIVIDEND:
        txnSplit = SplitTxn.makeSplitTxn(new_txn, parentAmt, splitAmt, splitRate, splitAcct, desc, 0, new_txn.getStatus())
        new_txn.addSplit(txnSplit)
    else:
        txnSplit = SplitTxn.makeSplitTxn(new_txn, parentAmt, splitAmt, splitRate, splitAcct, desc, 0, new_txn.getStatus())
        new_txn.addSplit(txnSplit)

    printTxn(new_txn, counter)

