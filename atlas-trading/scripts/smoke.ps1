$ErrorActionPreference = 'Stop'
$portfolio = Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/api/portfolios' -ContentType 'application/json' -Body (@{name='Smoke test';cash=1000000}|ConvertTo-Json)
$trade = Invoke-RestMethod -Method Post -Uri 'http://localhost:8081/api/trades' -ContentType 'application/json' -Body (@{clientKey=[guid]::NewGuid().ToString();portfolioId=$portfolio.id;symbol='AAPL';side='BUY';quantity=10;price=185}|ConvertTo-Json)
$deadline = (Get-Date).AddSeconds(60)
do {
  Start-Sleep -Seconds 1
  $result = Invoke-RestMethod "http://localhost:8081/api/trades/$($trade.id)"
} while ($result.status -eq 'PENDING_RISK' -and (Get-Date) -lt $deadline)
if ($result.status -ne 'EXECUTED') { throw "Trade failed or timed out: $($result | ConvertTo-Json)" }
do {
  Start-Sleep -Seconds 1
  $journals = Invoke-RestMethod 'http://localhost:8083/api/journals'
  $journal = $journals | Where-Object tradeId -eq $trade.id
} while (-not $journal -and (Get-Date) -lt $deadline)
if (-not $journal) { throw 'Accounting journal did not arrive' }
$debits = ($journal.lines | Measure-Object debit -Sum).Sum
$credits = ($journal.lines | Measure-Object credit -Sum).Sum
if ($debits -ne 1850 -or $credits -ne 1850) { throw 'Unexpected journal totals' }
$position = (Invoke-RestMethod "http://localhost:8081/api/portfolios/$($portfolio.id)/positions")[0]
if ($position.quantity -ne 10 -or $position.cost -ne 1850) { throw 'Unexpected position' }
Write-Host "PASS: trade $($trade.id) executed; position updated; balanced journal posted."
