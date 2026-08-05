#!/usr/bin/env python
"""
Moneydance Retirement Portfolio Summary Script
This script runs within the Moneydance Jython console or extension.
It filters for active Bank and Investment accounts, groups them by tax categories
(IRA, Roth/HSA, Taxable), and prints a summary of their balances, cash holdings,
and cost basis.
"""

# Accessing Moneydance API Globals
# Note: 'moneydance' is a pre-defined global entry point when run inside Moneydance.
global moneydance
mdGUI = moneydance.getUI()                  # Reference to the Moneydance GUI instance
book = moneydance.getCurrentAccountBook()   # The current data set / file loaded in Moneydance

import sys
import platform
from java.lang import System
from com.infinitekind.moneydance.model import AccountUtil, AcctFilter, Account, InvestUtil

# Import debugging utilities for newer Moneydance builds
if moneydance.getBuild() >= 5100:
    from com.infinitekind.util import AppDebug

class AcctTypeFilter(AcctFilter):
    """
    Filter to select only Bank and Investment accounts that are active.
    This implements Moneydance's AcctFilter interface.
    """
    my_types = [
        Account.AccountType.BANK,
        Account.AccountType.INVESTMENT,
    ]

    def format(self, acct):
        return acct.getAccountName()
    
    def matches(self, aNacct):
        """
        Determines whether the given account matches the filter criteria:
        1. Account type must be in my_types (Bank or Investment).
        2. Account must not be inactive.
        """
        acctType = aNacct.getAccountType()
        return acctType in self.my_types and not aNacct.getAccountIsInactive()

def myFunc(e):
    """Sorting key helper to sort accounts alphabetically by their name."""
    return e.getAccountName()

class AccountDivider():
    """
    Divides accounts into three major tax-treatment categories:
    - Roth/HSA accounts (tax-free growth)
    - IRA/Pension/Retirement/401k/457b/TIAA accounts (tax-deferred)
    - Taxable accounts (all other accounts, such as standard brokerage or bank accounts)
    """
    def __init__(self):
        self.roths = []
        self.iras = []
        self.taxables = []
    
    def add(self, acct):
        """
        Categorizes the account based on keywords in its name.
        Applies filtering to exclude empty accounts and specific personal accounts.
        """
        # Ignore accounts with zero balance
        if acct.getRecursiveBalance() == 0:
            return
        
        # Exclude accounts belonging to specific individuals or DAF (Donor Advised Fund)
        name_lower = acct.getAccountName().lower()
        if "christopher" in name_lower:
            return
        if "nikki" in name_lower:
            return
        if "dafgiving" in name_lower:
            return
        
        # Categorize into Roth / HSA (Tax-Free)
        if "roth" in name_lower or "hsa" in name_lower:
            self.roths.append(acct)
        # Categorize into IRA / Tax-Deferred Retirement
        elif any(keyword in name_lower for keyword in ["ira", "pension", "retirement", "401", "457", "tiaa"]):
            self.iras.append(acct)
        # All other accounts are classified as Taxable
        else:
            self.taxables.append(acct)

def getCostBasis(acct):
    """
    Calculates the total cost basis for a given account.
    
    For INVESTMENT accounts:
      - Iterates through security sub-accounts.
      - Calculates cost basis using Moneydance's InvestUtil.
      - Returns the sum of security cost basis and any uninvested cash (account balance).
    
    For other accounts (e.g., BANK):
      - Returns the current balance as the basis.
    """
    if acct.getAccountType() == Account.AccountType.INVESTMENT:
        total_investment_cost_basis = 0
        
        # Loop through all sub-accounts (which hold the actual security positions)
        for sub_acct in acct.getSubAccounts():
            if sub_acct.getAccountType() == Account.AccountType.SECURITY:
                # Get the cost basis for this specific security (returned as a long integer)
                # Moneydance stores values as long integers (e.g., $100.50 is stored as 10050)
                sec_cost_basis = InvestUtil.getCostBasis(sub_acct)
                total_investment_cost_basis += sec_cost_basis
                
        # Total cost basis is security cost basis plus cash balance in the investment account
        return total_investment_cost_basis + acct.getBalance()
    else:
        # Bank accounts do not have security cost basis; their basis is the current balance.
        return acct.getBalance()

def formatCurrency(acct, amount):
    """Formats a currency amount using the format rules of the account's currency type."""
    return acct.getCurrencyType().formatFancy(amount, '.')

def sortAndSum(grp):
    """
    Sorts a group of accounts alphabetically by name and calculates sums for:
    - Recursive Balance (total value including sub-accounts)
    - Cash Balance (uninvested cash or liquid balance)
    - Cost Basis (total money invested or basis)
    
    Returns:
      tuple: (sorted_group, total_recursive_balance, total_cash, total_basis)
    """
    grp.sort(key=myFunc)
    total = 0
    cash = 0
    basis = 0
    for acct in grp:
        total += acct.getRecursiveBalance()
        cash += acct.getBalance()
        basis += getCostBasis(acct)
    return grp, total, cash, basis

def printAcctGroup(grp):
    """Prints detailed information for each account in the group."""
    for acct in grp:
        # Note: Moneydance values are stored as integers (e.g. cents). We divide by 100 to print as standard dollar amounts.
        print("\t{}: ${:,.0f} from ${:,.0f} basis (${:,.0f} cash)".format(
            acct.getAccountName(), 
            acct.getRecursiveBalance() / 100.0, 
            getCostBasis(acct) / 100.0, 
            acct.getBalance() / 100.0
        ))

# Print environment and system information
print("")
print("Java version:              %s" % (System.getProperty("java.version")))
print("Py(Jy)thon platform:       %s %s %s.%s" % (
    platform.python_implementation(), 
    platform.system(), 
    sys.version_info.major, 
    sys.version_info.minor
))
print("")
print("Moneydance version:        MD%s(%s)" % (moneydance.getVersion(), moneydance.getBuild()))
print("The Moneydance controller: %s >> (the global variable 'moneydance' accesses this key object)" % (moneydance))
print("The UI:                    %s" % (mdGUI))
print("The current data set:      '%s'" % (book))

if book is not None:
    tSet = book.getTransactionSet()
    allAccounts = AccountUtil.allMatchesForSearch(book, AcctTypeFilter())
    print("")
    print("Number of transactions in this dataset: %s" % (tSet.getTransactionCount()))
    
    # Calculate account/category counts excluding Root account and categories
    print("Number of Accounts:                     %s" % (
        len([a for a in allAccounts if not a.getAccountType().isCategory() and a.getAccountType() != Account.AccountType.ROOT])
    ))
    print("Number of Categories:                   %s" % (
        len([a for a in allAccounts if a.getAccountType().isCategory()])
    ))
    print("------")
    
    msgStr = "Hello world... Printing to Moneydance's console from this python script...."
    if moneydance.getBuild() >= 5100:
        AppDebug.ALL.log(msgStr)        # Post-MD2024 logging utility
    else:
        System.err.println(msgStr)      # Pre-MD2024 standard error console fallback

    # Categorize and divide the loaded accounts
    groups = AccountDivider()
    for acct in allAccounts:
        groups.add(acct)
    
    # Calculate and sum all groups first
    iras, iratotal, iracash, irabasis = sortAndSum(groups.iras)
    roths, rothtotal, rothcash, rothbasis = sortAndSum(groups.roths)
    taxables, taxabletotal, taxablecash, taxablebasis = sortAndSum(groups.taxables)

    # Calculate Grand Totals
    grandtotal = iratotal + rothtotal + taxabletotal
    grandcash = iracash + rothcash + taxablecash

    print("Totals by Account Type: <total> (<cash>) from <total before gains and contributions>")
    
    # 1. Print Grand Totals
    # Note: The taxablebasis is printed here to align with the original behavior of the script.
    # To print the sum of all bases (IRA + Roth + Taxable basis), uncomment the next line:
    # grandbasis = irabasis + rothbasis + taxablebasis
    print("Grand total: ${:,.0f} from ${:,.0f} taxable basis (${:,.0f} cash)".format(
        float(grandtotal)/100, float(taxablebasis)/100, float(grandcash)/100
    ))

    # 2. Process and print IRA Accounts
    print("IRA total: ${:,.0f} from ${:,.0f} IRA basis (${:,.0f} cash)".format(
        float(iratotal)/100, float(irabasis)/100, float(iracash)/100
    ))
    printAcctGroup(iras)
    
    # 3. Process and print Roth / HSA Accounts
    print("Roth total: ${:,.0f} from ${:,.0f} Roth basis (${:,.0f} cash)".format(
        float(rothtotal)/100, float(rothbasis)/100, float(rothcash)/100
    ))
    printAcctGroup(roths)
    
    # 4. Process and print Taxable Accounts
    print("Taxable total: ${:,.0f} from ${:,.0f} taxable basis (${:,.0f} cash)".format(
        float(taxabletotal)/100, float(taxablebasis)/100, float(taxablecash)/100
    ))
    printAcctGroup(taxables)
