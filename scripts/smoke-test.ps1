# Prueba rápida para Windows PowerShell (5.1 o 7). Requiere el sistema levantado con APP_SEED_ENABLED=true.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$envMap = @{}
Get-Content .env | Where-Object { $_ -match '^\s*[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
  $k, $v = $_ -split '=', 2; $envMap[$k.Trim()] = ($v -replace '\s+#.*$', '').Trim()
}
$port = if ($envMap['NGINX_PORT']) { $envMap['NGINX_PORT'] } else { '48124' }
$pass = if ($envMap['APP_SEED_PASSWORD']) { $envMap['APP_SEED_PASSWORD'] } else { 'Demo#2026!' }
$base = "http://localhost:$port/api/v1"
$runId = [guid]::NewGuid().ToString('N').Substring(0, 8)
$fails = 0

function Sha256Hex([string]$value) {
  $sha = [System.Security.Cryptography.SHA256]::Create()
  try {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($value)
    return (([BitConverter]::ToString($sha.ComputeHash($bytes)) -replace '-', '')).ToLowerInvariant()
  } finally { $sha.Dispose() }
}

function HmacSha256Hex([string]$value, [string]$secret) {
  $hmac = New-Object System.Security.Cryptography.HMACSHA256
  try {
    $hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($secret)
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($value)
    return (([BitConverter]::ToString($hmac.ComputeHash($bytes)) -replace '-', '')).ToLowerInvariant()
  } finally { $hmac.Dispose() }
}

function Call($method, $path, $token = $null, $body = $null, $extra = @{}) {
  $h = @{}; if ($token) { $h['Authorization'] = "Bearer $token" }
  foreach ($k in $extra.Keys) { $h[$k] = $extra[$k] }
  $args = @{ Uri = "$base$path"; Method = $method; Headers = $h; UseBasicParsing = $true }
  if ($body) { $args['Body'] = ($body | ConvertTo-Json -Compress -Depth 10); $args['ContentType'] = 'application/json' }
  elseif ($method -in 'POST', 'PUT', 'PATCH') { $args['Body'] = '{}'; $args['ContentType'] = 'application/json' }  # PowerShell enviaria form-urlencoded por defecto
  try { $r = Invoke-WebRequest @args; $status = [int]$r.StatusCode; $text = $r.Content }
  catch {
    if ($_.Exception.Response) {
      $status = [int]$_.Exception.Response.StatusCode
      try { $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); $text = $sr.ReadToEnd() } catch { $text = '' }
    } else { throw }
  }
  $json = $null; if ($text) { try { $json = $text | ConvertFrom-Json } catch {} }
  return [pscustomobject]@{ Status = $status; Json = $json }
}
function Cnt($j) { if ($null -eq $j) { return 0 } else { return @($j).Count } }
function Check($name, $cond) { if ($cond) { Write-Host "  OK   $name" -ForegroundColor Green } else { Write-Host "  FAIL $name" -ForegroundColor Red; $script:fails++ } }

Write-Host '1) health'
$h = Invoke-WebRequest -Uri "http://localhost:$port/actuator/health" -UseBasicParsing
Check 'health 200' ($h.StatusCode -eq 200)

Write-Host '2) admin con 2 copropiedades'
$login = Call POST '/auth/login' $null @{ email = 'admin@vecindad.local'; password = $pass }
Check 'login 200' ($login.Status -eq 200)
$norte = ($login.Json.tenants | Where-Object { $_.slug -eq 'demo_norte' }).id
$sur   = ($login.Json.tenants | Where-Object { $_.slug -eq 'demo_sur' }).id
Check 'dos copropiedades' ($norte -and $sur)
$sel = Call POST '/auth/select-tenant' $login.Json.accessToken @{ tenantId = $norte }
Check 'select-tenant norte' ($sel.Status -eq 200)
$admin = $sel.Json.accessToken

Write-Host '3) datos de Fase 1 y 2 en Norte'
Check 'inmuebles 200' ((Call GET '/properties?size=5' $admin).Status -eq 200)
$res = Call GET '/residents?size=10' $admin
Check 'personas demo' ($res.Status -eq 200 -and $res.Json.totalElements -ge 3)
$veh = Call GET '/vehicles?plate=ABC123' $admin
Check 'vehiculo ABC123' ($veh.Status -eq 200 -and $veh.Json.totalElements -eq 1)
$car = $veh.Json.content[0]

Write-Host '4) porteria: entrada, duplicado, vehiculo dentro no se modifica, salida'
$guard = (Call POST '/auth/login' $null @{ email = 'portero@demo-norte.local'; password = $pass }).Json.accessToken
Check 'lookup ABC123' ((Call GET '/access/vehicles/lookup?plate=ABC123' $guard).Status -eq 200)
Check 'entrada 201' ((Call POST '/access/entry' $guard @{ plate = 'ABC123' }).Status -eq 201)
$dup = Call POST '/access/entry' $guard @{ plate = 'ABC123' }
Check 'segunda entrada 409' ($dup.Status -eq 409 -and $dup.Json.code -eq 'VEHICLE_ALREADY_INSIDE')
$mod = Call PUT "/vehicles/$($car.id)" $admin @{ typeCode = 'CARRO'; plate = 'ABC123'; unitId = $car.unitId; color = 'Verde' }
Check 'modificar vehiculo dentro 409' ($mod.Status -eq 409 -and $mod.Json.code -eq 'VEHICLE_INSIDE')
Write-Host "       mensaje: $($mod.Json.message)"
Check 'salida 201' ((Call POST '/access/exit' $guard @{ plate = 'ABC123' }).Status -eq 201)
Check 'portero no ve personas (403)' ((Call GET '/residents' $guard).Status -eq 403)

Write-Host '5) propietario con inmuebles en 2 copropiedades'
$o = Call POST '/auth/login' $null @{ email = 'propietario@demo-norte.local'; password = $pass }
$ot = (Call POST '/auth/select-tenant' $o.Json.accessToken @{ tenantId = $norte }).Json.accessToken
$mine = Call GET '/my/units' $ot
Check 've sus 3 inmuebles de Norte' ($mine.Status -eq 200 -and @($mine.Json).Count -eq 3)
Check 'no lista residentes (403)' ((Call GET '/residents' $ot).Status -eq 403)
$ots = (Call POST '/auth/select-tenant' $o.Json.accessToken @{ tenantId = $sur }).Json.accessToken
Check 'en Sur ve 1 inmueble' (@((Call GET '/my/units' $ots).Json).Count -eq 1)

Write-Host '6) aislamiento'
$s = (Call POST '/auth/login' $null @{ email = 'admin.sur@demo-sur.local'; password = $pass }).Json.accessToken
Check 'admin.sur no entra a Norte (403)' ((Call POST '/auth/select-tenant' $s @{ tenantId = $norte }).Status -eq 403)
Check 'sin token (401)' ((Call GET '/properties').Status -eq 401)

Write-Host '7) visitantes: QR de un solo uso y autorizacion en tiempo real'
$unit101 = ($mine.Json | Where-Object { $_.identifier -eq 'T1-101' }).unitId
$to = (Get-Date).ToUniversalTime().AddHours(2).ToString('o')
$inv = Call POST '/my/visitors/invitations' $ot @{ unitId = $unit101; visitorName = 'Carlos Gomez'; peopleCount = 2; validTo = $to }
Check 'invitacion con QR (201)' ($inv.Status -eq 201 -and $inv.Json.qrToken)
$qr = $inv.Json.qrToken
Check 'validar QR sin ingresar' ((Call POST '/visitors/validate-qr' $guard @{ token = $qr }).Json.valid -eq $true)
$ci = Call POST '/visitors/check-in-qr' $guard @{ token = $qr }
Check 'ingreso con QR (201, INSIDE)' ($ci.Status -eq 201 -and $ci.Json.status -eq 'INSIDE')
$again = Call POST '/visitors/check-in-qr' $guard @{ token = $qr }
Check 'QR reutilizado (409 QR_ALREADY_USED)' ($again.Status -eq 409 -and $again.Json.code -eq 'QR_ALREADY_USED')
Check 'salida del visitante' ((Call POST "/visitors/visits/$($ci.Json.id)/check-out" $guard).Status -eq 200)
$wi = Call POST '/visitors/walk-in' $guard @{ unitId = $unit101; visitorName = 'Visitante sin invitacion' }
Check 'solicitud sin invitacion (201, PENDING_AUTH)' ($wi.Status -eq 201 -and $wi.Json.status -eq 'PENDING_AUTH')
Check 'ingreso sin autorizacion (409)' ((Call POST "/visitors/visits/$($wi.Json.id)/check-in" $guard).Status -eq 409)
Check 'residente ve la solicitud pendiente' ((Cnt (Call GET '/my/visitors/requests?status=PENDING_AUTH' $ot).Json) -ge 1)
Check 'residente autoriza' ((Call POST "/my/visitors/requests/$($wi.Json.id)/authorize" $ot).Json.status -eq 'AUTHORIZED')
Check 'ingreso autorizado (INSIDE)' ((Call POST "/visitors/visits/$($wi.Json.id)/check-in" $guard).Json.status -eq 'INSIDE')
Check 'salida' ((Call POST "/visitors/visits/$($wi.Json.id)/check-out" $guard).Status -eq 200)

Write-Host '8) paquetes, novedades y resumen de seguridad'
$pk = Call POST '/packages' $guard @{ unitId = $unit101; recipientName = 'Juan Perez'; carrier = 'Servientrega'; trackingNumber = 'SV-SMOKE' }
Check 'paquete recibido (201)' ($pk.Status -eq 201 -and $pk.Json.status -eq 'RECEIVED')
Check 'residente ve sus paquetes' ((Cnt (Call GET '/my/packages' $ot).Json) -ge 1)
Check 'entrega registrada' ((Call POST "/packages/$($pk.Json.id)/deliver" $guard @{ deliveredTo = 'Juan Perez' }).Json.status -eq 'DELIVERED')
Check 'segunda entrega (409)' ((Call POST "/packages/$($pk.Json.id)/deliver" $guard @{ deliveredTo = 'Otro' }).Status -eq 409)
$inc = Call POST '/incidents' $guard @{ category = 'SEGURIDAD'; description = 'Puerta del sotano abierta'; location = 'Sotano 1' }
Check 'novedad registrada (201)' ($inc.Status -eq 201)
Check 'residente no ve novedades (403)' ((Call GET '/incidents' $ot).Status -eq 403)
Check 'administrador ve novedades' ((Call GET '/incidents' $admin).Status -eq 200)
$sum = Call GET '/security/summary' $guard
Check 'resumen de seguridad' ($sum.Status -eq 200 -and $null -ne $sum.Json.vehiclesInside)

Write-Host '9) cartera: imputacion, idempotencia, estado de cuenta y privacidad'
$acc = (Call POST '/auth/login' $null @{ email = 'contador@demo-norte.local'; password = $pass }).Json.accessToken
Check 'contador ve la configuracion financiera' ((Call GET '/billing/settings' $acc).Status -eq 200)
$pf = Call GET '/billing/portfolio?overdueOnly=true&size=50' $acc
Check 'cartera con mora (totales)' ($pf.Status -eq 200 -and $pf.Json.totalOverdue -gt 0)
$row = $pf.Json.units.content | Where-Object { $_.unitIdentifier -ne 'T1-101' } | Select-Object -First 1
$before = (Call GET "/billing/units/$($row.unitId)/statement" $acc).Json.closingBalance
$key = 'smoke-' + [guid]::NewGuid().ToString()
$p1 = Call POST '/payments' $acc @{ unitId = $row.unitId; amount = 50000; method = 'CASH'; reference = 'SMOKE' } @{ 'Idempotency-Key' = $key }
Check 'pago registrado (201)' ($p1.Status -eq 201 -and @($p1.Json.allocations).Count -ge 1)
$p2 = Call POST '/payments' $acc @{ unitId = $row.unitId; amount = 50000; method = 'CASH'; reference = 'SMOKE' } @{ 'Idempotency-Key' = $key }
Check 'reintento con la misma llave (200, mismo pago)' ($p2.Status -eq 200 -and $p2.Json.payment.id -eq $p1.Json.payment.id)
$after = (Call GET "/billing/units/$($row.unitId)/statement" $acc).Json.closingBalance
Check 'el saldo bajo una sola vez (50.000)' (($before - $after) -eq 50000)
Check 'reversion con motivo' ((Call POST "/payments/$($p1.Json.payment.id)/reverse" $acc @{ reason = 'Prueba de humo' }).Json.payment.status -eq 'REVERSED')
Check 'libro de movimientos' ((Call GET "/billing/units/$($row.unitId)/ledger" $acc).Json.totalElements -ge 3)
Check 'propietario ve su estado de cuenta' ((Call GET "/my/statement?unitId=$unit101" $ot).Status -eq 200)
Check 'propietario no ve la cartera (403)' ((Call GET '/billing/portfolio' $ot).Status -eq 403)
$secr = (Call POST '/auth/login' $null @{ email = 'secretaria@demo-norte.local'; password = $pass }).Json.accessToken
Check 'secretaria sin acceso financiero (403)' ((Call GET '/billing/portfolio' $secr).Status -eq 403)
$cons = (Call POST '/auth/login' $null @{ email = 'consejo@demo-norte.local'; password = $pass }).Json.accessToken
Check 'consejo ve cartera (200)' ((Call GET '/billing/portfolio' $cons).Status -eq 200)
Check 'consejo no crea cargos (403)' ((Call POST '/billing/charges' $cons @{ unitId = $unit101; type = 'FINE'; description = 'x'; dueDate = (Get-Date).ToString('yyyy-MM-dd'); amount = 1000 }).Status -eq 403)
Check 'intereses de mora (corrida idempotente)' ((Call POST '/billing/runs/interest' $admin @{}).Status -eq 200)

Write-Host '10) conciliacion bancaria: CSV, coincidencias, duplicados y revision manual'
$tag = 'SMOKE-' + [guid]::NewGuid().ToString().Substring(0, 8)
$dd = (Get-Date).AddDays(-1).ToString('dd/MM/yyyy')
$csvPath = Join-Path $env:TEMP "extracto-$tag.csv"
@('Fecha,Descripcion,Referencia,Valor,Nombre',
  "$dd,PAGO ADMON T1-102 JUAN PEREZ,$tag-1,`"300.000,00`",JUAN PEREZ",
  "$dd,CONSIGNACION SIN IDENTIFICAR,$tag-2,`"45.000,00`",PEDRO LOPEZ",
  "$dd,COMISION BANCARIA,$tag-3,`"-12.500,00`",") | Set-Content -Path $csvPath -Encoding UTF8
function Upload($token, $path) {
  $out = curl.exe -s -X POST -H "Authorization: Bearer $token" -F "file=@$path;type=text/csv" -F 'bankName=Banco Demo' -F 'accountLabel=Cta Demo' "$base/bank-reconciliation/imports"
  return ($out | ConvertFrom-Json)
}
$up = Upload $acc $csvPath
Check 'extracto aceptado (QUEUED)' ($up.status -eq 'QUEUED' -and $up.id)
$imp = $null
for ($i = 0; $i -lt 40; $i++) {
  $imp = (Call GET "/bank-reconciliation/imports/$($up.id)" $acc).Json
  if ($imp.status -eq 'COMPLETED' -or $imp.status -eq 'FAILED') { break }
  Start-Sleep -Milliseconds 500
}
Check 'importacion terminada (COMPLETED)' ($imp.status -eq 'COMPLETED')
Check '3 filas: 1 ignorada (egreso) y 2 clasificadas' ($imp.totalRows -eq 3 -and $imp.ignored -eq 1 -and ($imp.autoMatched + $imp.manualReview + $imp.ambiguous) -eq 2)
$rowsRes = (Call GET "/bank-reconciliation/rows?importId=$($up.id)&size=50" $acc).Json.content
$payRow = $rowsRes | Where-Object { $_.description -like '*T1-102*' } | Select-Object -First 1
$u102 = ((Call GET '/billing/portfolio?size=100' $acc).Json.units.content | Where-Object { $_.unitIdentifier -eq 'T1-102' }).unitId
if ($payRow.status -ne 'AUTO_MATCHED') {
  $m = Call POST "/bank-reconciliation/rows/$($payRow.id)/match" $acc @{ unitId = $u102; reason = 'Prueba de humo' }
  Check 'conciliacion manual (MATCHED)' ($m.Status -eq 200 -and $m.Json.row.status -eq 'MATCHED')
} else {
  Check 'la fila de alta confianza se aplico sola (AUTO_MATCHED)' $true
}
$rv = Call POST "/bank-reconciliation/rows/$($payRow.id)/revert" $acc @{ reason = 'Prueba de humo: deshacer' }
Check 'conciliacion revertida (vuelve a revision)' ($rv.Status -eq 200 -and $rv.Json.row.status -eq 'MANUAL_REVIEW' -and @($rv.Json.decisions).Count -ge 2)
$dupUp = Upload $acc $csvPath
Check 'mismo archivo otra vez (409 IMPORT_ALREADY_EXISTS)' ($dupUp.code -eq 'IMPORT_ALREADY_EXISTS')
$secrUp = Upload $secr $csvPath
Check 'secretaria no concilia (403)' ($secrUp.code -eq 'FORBIDDEN')
Remove-Item $csvPath -ErrorAction SilentlyContinue

Write-Host '11) Wompi (pagos en linea) y Certificados PDF'
$superLogin = Call POST '/auth/login' $null @{ email = 'superadmin@vecindad.local'; password = $pass }
$superToken = $superLogin.Json.accessToken
$feat = Call PUT "/platform/tenants/$norte/features/ONLINE_PAYMENTS" $superToken @{ enabled = $true }
Check 'feature flag ONLINE_PAYMENTS activado' ($feat.Status -eq 200 -and $feat.Json.ONLINE_PAYMENTS -eq $true)

$wpLow = Call POST '/my/payments/online' $ot @{ unitId = $unit101; amount = 1000; notes = 'Monto minimo' }
Check 'monto menor al minimo Wompi rechazado (400)' ($wpLow.Status -eq 400)
$wpReq = Call POST '/my/payments/online' $ot @{ unitId = $unit101; amount = 25000; notes = 'Pago cuota en linea' }
Check 'intencion Wompi creada (201)' ($wpReq.Status -eq 201 -and $wpReq.Json.reference -like 'WP-*' -and $wpReq.Json.signature.Length -eq 64)
$wpRef = $wpReq.Json.reference
$wpNet = $wpReq.Json.netAmountInCents
$wpTotal = $wpReq.Json.amountInCents
Check 'total con comision de pasarela y tarifa de plataforma' ($wpNet -eq 2500000 -and $wpTotal -gt $wpNet -and $wpReq.Json.platformFeeInCents -ge 0)
$wpQuote = Call GET "/my/payments/online/quote?amount=25000" $ot
Check 'cotizacion de pago en linea (200)' ($wpQuote.Status -eq 200 -and $wpQuote.Json.totalInCents -gt 2500000)

$wpStatus = Call GET "/my/payments/online/$wpRef" $ot
Check 'intencion en estado PENDING' ($wpStatus.Status -eq 200 -and $wpStatus.Json.status -eq 'PENDING')

$wpTimestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$wpChecksum = Sha256Hex("tx-$wpRef" + "APPROVED" + "$wpTotal" + "COP" + "$wpTimestamp" + "$($envMap['WOMPI_EVENTS_SECRET'])")
$wpEvt = @{
  event = 'transaction.updated'
  data = @{
    transaction = @{
      id = "tx-$wpRef"
      reference = $wpRef
      amount_in_cents = $wpTotal
      currency = 'COP'
      status = 'APPROVED'
      payment_method_type = 'CARD'
      fee = @{ amount_in_cents = 120000 }
    }
  }
  environment = 'test'
  signature = @{
    properties = @('transaction.id', 'transaction.status', 'transaction.amount_in_cents', 'transaction.currency')
    checksum = $wpChecksum
  }
  timestamp = $wpTimestamp
}
$hook = Call POST '/public/wompi/events' $null $wpEvt
Check 'webhook Wompi procesado (200)' ($hook.Status -eq 200)

$wpApproved = Call GET "/my/payments/online/$wpRef" $ot
Check 'intencion actualizada a APPROVED' ($wpApproved.Status -eq 200 -and $wpApproved.Json.status -eq 'APPROVED' -and $wpApproved.Json.paymentId)

$wpStatement = Call GET "/my/statement?unitId=$unit101" $ot
$wpEntry = @($wpStatement.Json.lines | Where-Object { $_.entryType -eq 'PAYMENT' -and $_.paymentId -eq $wpApproved.Json.paymentId })[0]
Check 'pago acreditado por el valor de la deuda sin la tarifa' ($wpEntry -and [math]::Abs([math]::Abs([double]$wpEntry.amount) - 25000) -lt 0.01)

$hookReplay = Call POST '/public/wompi/events' $null $wpEvt
Check 'webhook Wompi idempotente (200)' ($hookReplay.Status -eq 200)

$pdfStmt = Invoke-WebRequest -Uri "$base/my/certificates/statement?unitId=$unit101" -Headers @{ Authorization = "Bearer $ot" } -UseBasicParsing
Check 'Estado de cuenta PDF generado (200, %PDF)' ($pdfStmt.StatusCode -eq 200 -and $pdfStmt.Headers['Content-Type'] -like '*pdf*' -and $pdfStmt.RawContent.Length -gt 500)

$pdfProp = Invoke-WebRequest -Uri "$base/my/certificates/ownership?unitId=$unit101" -Headers @{ Authorization = "Bearer $ot" } -UseBasicParsing
Check 'Certificado de propiedad PDF generado (200, %PDF)' ($pdfProp.StatusCode -eq 200 -and $pdfProp.Headers['Content-Type'] -like '*pdf*')

$hist = Call GET "/my/certificates/history?unitId=$unit101" $ot
Check 'historial de certificados emitidos' ($hist.Status -eq 200 -and @($hist.Json).Count -ge 2)
$lastCode = $hist.Json[0].verificationCode

$pubVer = Call GET "/public/certificates/verify/$lastCode" $null
Check 'verificacion publica del certificado (200, VALID)' ($pubVer.Status -eq 200 -and $pubVer.Json.status -eq 'VALID' -and $pubVer.Json.unitIdentifier -eq 'T1-101')

Write-Host '12) Zonas comunes y Reservas'
$areas = Call GET '/common-areas' $admin
Check 'zonas comunes creadas (al menos 3)' ($areas.Status -eq 200 -and @($areas.Json).Count -ge 3)
$cancha = $areas.Json | Where-Object { $_.name -like '*Cancha*' } | Select-Object -First 1
$salon = $areas.Json | Where-Object { $_.name -like '*Salón*' } | Select-Object -First 1

$rndDays = (Get-Random -Minimum 2 -Maximum 5)
$todayStr = (Get-Date).AddDays($rndDays).ToString('yyyy-MM-dd')
$avail = Call GET "/common-areas/$($salon.id)/availability?date=$todayStr" $ot
Check 'disponibilidad de horarios (franjas)' ($avail.Status -eq 200 -and @($avail.Json).Count -gt 0)

$resStart = (Get-Date).AddDays($rndDays).Date.AddHours(14).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
$resEnd = (Get-Date).AddDays($rndDays).Date.AddHours(15).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

$res1 = Call POST '/my/reservations' $ot @{
  commonAreaId = $cancha.id
  unitId = $unit101
  startTime = $resStart
  endTime = $resEnd
  paymentMethod = 'NONE'
  acceptedTerms = $true
  notes = 'Partido de microfútbol'
}
Check 'reserva creada exitosamente (201, APPROVED)' ($res1.Status -eq 201 -and $res1.Json.status -eq 'APPROVED')
$resId = $res1.Json.id

$resDup = Call POST '/my/reservations' $ot @{
  commonAreaId = $cancha.id
  unitId = $unit101
  startTime = $resStart
  endTime = $resEnd
  paymentMethod = 'NONE'
  acceptedTerms = $true
  notes = 'Reserva duplicada'
}
Check 'traslape de horario rechazado (409 SLOT_ALREADY_RESERVED)' ($resDup.Status -eq 409 -and $resDup.Json.code -eq 'SLOT_ALREADY_RESERVED')

$unit201 = ($mine.Json | Where-Object { $_.identifier -eq 'T1-201' }).unitId
if ($unit201) {
  $resDebt = Call POST '/my/reservations' $ot @{
    commonAreaId = $cancha.id
    unitId = $unit201
    startTime = (Get-Date).AddDays($rndDays + 1).Date.AddHours(10).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    endTime = (Get-Date).AddDays($rndDays + 1).Date.AddHours(11).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    paymentMethod = 'NONE'
    acceptedTerms = $true
  }
  Check 'bloqueo de reservas por mora (409 RESERVATIONS_BLOCKED_BY_DEBT)' ($resDebt.Status -eq 409 -and $resDebt.Json.code -eq 'RESERVATIONS_BLOCKED_BY_DEBT')
}

$cancelRes = Call POST "/my/reservations/$resId/cancel" $ot @{ reason = 'Cancelación por prueba de humo' }
Check 'reserva cancelada por el residente' ($cancelRes.Status -eq 200 -and $cancelRes.Json.status -eq 'CANCELLED')

Write-Host '13) PQRS y Convivencia'
$pqrReq = Call POST '/my/pqrs' $ot @{
  unitId = $unit101
  category = 'CONVIVENCIA_RUIDO'
  priority = 'HIGH'
  title = 'Alerta preventiva de ruido en horas de descanso'
  description = 'Vecino con parlante a alto volumen después de medianoche'
  isAnonymous = $false
}
Check 'PQR radicada (201, ticket PQR-*, SLA 24h)' ($pqrReq.Status -eq 201 -and $pqrReq.Json.ticketNumber -like 'PQR-*' -and $pqrReq.Json.slaTargetHours -eq 24)
$pqrId = $pqrReq.Json.id

$sla = Call GET '/pqrs/metrics/sla' $admin
Check 'metricas de SLA de PQRS (totalOpen >= 1)' ($sla.Status -eq 200 -and $sla.Json.totalOpen -ge 1)

$pqrMsgPub = Call POST "/pqrs/$pqrId/messages" $admin @{
  message = 'Se procedió con visita de portería y llamado de atención.'
  isInternal = $false
}
Check 'respuesta administrativa publica agregada (201)' ($pqrMsgPub.Status -eq 201)

$pqrMsgPriv = Call POST "/pqrs/$pqrId/messages" $admin @{
  message = 'Nota privada: se dejará constancia en libro de minutas de portería.'
  isInternal = $true
}
Check 'nota interna privada agregada (201)' ($pqrMsgPriv.Status -eq 201)

$myPqr = Call GET "/my/pqrs/$pqrId" $ot
Check 'residente ve respuesta publica pero NO nota privada' ($myPqr.Status -eq 200 -and @($myPqr.Json.messages).Count -eq 1)

$closePqr = Call PATCH "/pqrs/$pqrId/status" $admin @{
  status = 'RESOLVED'
  resolutionNotes = 'Compromiso de convivencia firmado por el infractor.'
}
Check 'PQR resuelta (RESOLVED)' ($closePqr.Status -eq 200 -and $closePqr.Json.status -eq 'RESOLVED')

Write-Host '14) Comunicados y Notificaciones'
$comReq = Call POST '/communications' $admin @{
  title = 'Aviso de Mantenimiento de Tanques de Agua'
  summary = 'Lavado de tanques el próximo sábado'
  content = 'Se informa a la comunidad que se realizará corte preventivo del servicio...'
  priority = 'IMPORTANT'
  targetAudience = 'ALL'
  isPinned = $true
  publishNow = $true
}
Check 'comunicado creado y publicado (201, PUBLISHED)' ($comReq.Status -eq 201 -and $comReq.Json.status -eq 'PUBLISHED')
$comId = $comReq.Json.id

$myComms = Call GET '/my/communications' $ot
Check 'residente ve comunicados' ($myComms.Status -eq 200 -and @($myComms.Json.content).Count -ge 1)

$markRead = Call POST "/my/communications/$comId/read" $ot @{}
Check 'comunicado marcado como leido' ($markRead.Status -eq 204)

$notifSet = Call GET '/notifications/settings' $admin
Check 'configuracion de notificaciones consultada' ($notifSet.Status -eq 200 -and $notifSet.Json.emailEnabled -eq $true)

$remDispatched = Call POST '/notifications/portfolio-reminders' $admin @{}
Check 'recordatorios de cartera vencida disparados' ($remDispatched.Status -eq 200 -and $remDispatched.Json.status -eq 'SUCCESS')

$notifLogs = Call GET '/notifications/logs' $admin
Check 'logs de notificaciones registrados' ($notifLogs.Status -eq 200 -and @($notifLogs.Json.content).Count -ge 1)

Write-Host '15) Almacenamiento y Documentos'
$docTmp = Join-Path $env:TEMP 'reglamento-smoke.txt'
'REGLAMENTO DE PROPIEDAD HORIZONTAL - CODIGO DE CONVIVENCIA' | Out-File -FilePath $docTmp -Encoding utf8

$docUp = & curl.exe -s -F "file=@$docTmp" -F "title=Reglamento Interno de Propiedad Horizontal" -F "category=REGLAMENTO_PROPIEDAD_HORIZONTAL" -F "minimumRole=RESIDENTE" -H "Authorization: Bearer $admin" "$base/documents" | ConvertFrom-Json
Check 'documento subido con StoragePort (201)' ($docUp.id -and $docUp.fileName -like '*reglamento*')
$docId = $docUp.id

$docList = Call GET '/my/documents' $ot
Check 'residente lista documentos disponibles' ($docList.Status -eq 200 -and @($docList.Json).Count -ge 1)

$docDown = Invoke-WebRequest -Uri "$base/my/documents/$docId/download" -Headers @{ Authorization = "Bearer $ot" } -UseBasicParsing
Check 'residente descarga documento y verifica contenido' ($docDown.StatusCode -eq 200 -and $docDown.Content -like '*REGLAMENTO*')
Remove-Item $docTmp -ErrorAction SilentlyContinue

Write-Host '16) Asambleas y Votacion Ponderada (Ley 675)'
$asms = Call GET '/assemblies' $admin
Check 'asamblea demo consultada' ($asms.Status -eq 200 -and @($asms.Json).Count -ge 1)
$demoAsmId = $asms.Json[0].id

$newAsm = Call POST '/assemblies' $admin @{
  title = "Asamblea General Extraordinaria $((Get-Random))"
  type = 'EXTRAORDINARIA'
  scheduledAt = (Get-Date).AddDays(5).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
  location = 'Salón Comunal'
  isVirtual = $false
  requiredQuorumPercentage = 51.00
}
$asmId = $newAsm.Json.id

$qInit = Call GET "/assemblies/$asmId/quorum" $admin
Check 'quorum inicial (0%)' ($qInit.Status -eq 200 -and $qInit.Json.registeredUnits -eq 0)

$cin1 = Call POST "/assemblies/$asmId/check-in" $admin @{ unitId = $unit101; isProxy = $false }
$cin2 = Call POST "/assemblies/$asmId/check-in" $admin @{ unitId = $u102; isProxy = $true; proxyName = 'Apoderado Juan' }
Check 'asistencia registrada para inmuebles' ($cin1.Status -eq 200 -and $cin2.Status -eq 200)

$qAfter = Call GET "/assemblies/$asmId/quorum" $admin
Check 'quorum recalculado con coeficientes de asistentes' ($qAfter.Status -eq 200 -and $qAfter.Json.registeredUnits -eq 2 -and $qAfter.Json.currentQuorumPercentage -gt 0)

$topics = Call GET "/assemblies/$demoAsmId/topics" $admin
Check 'temas de votacion existentes' ($topics.Status -eq 200 -and @($topics.Json).Count -ge 1)

$newTop = Call POST "/assemblies/$asmId/topics" $admin @{
  orderNum = 2
  title = "Votacion prueba de humo $((Get-Random))"
  votingType = 'COEFFICIENT'
  requiresQualifiedMajority = $false
  options = @('A FAVOR', 'EN CONTRA', 'ABSTENCION')
}
$topicId = $newTop.Json.id

$openVote = Call POST "/assemblies/topics/$topicId/open-vote" $admin @{}
Check 'votacion abierta para el tema' ($openVote.Status -eq 200 -and $openVote.Json.status -eq 'VOTING_OPEN')

$vote1 = Call POST "/my/assemblies/topics/$topicId/vote" $ot @{ unitId = $unit101; choice = 'A FAVOR' }
Check 'voto emitido por residente de T1-101 (200)' ($vote1.Status -eq 200 -and $vote1.Json.choice -eq 'A FAVOR')

$voteDup = Call POST "/my/assemblies/topics/$topicId/vote" $ot @{ unitId = $unit101; choice = 'EN CONTRA' }
Check 'intento de segundo voto rechazado (409 VOTE_ALREADY_CAST)' ($voteDup.Status -eq 409 -and $voteDup.Json.code -eq 'VOTE_ALREADY_CAST')

$closeVote = Call POST "/assemblies/topics/$topicId/close-vote" $admin @{}
Check 'votacion cerrada por administracion' ($closeVote.Status -eq 200 -and $closeVote.Json.status -eq 'VOTING_CLOSED')

$results = Call GET "/assemblies/topics/$topicId/results" $admin
Check 'resultados certificados (A FAVOR ganador)' ($results.Status -eq 200 -and $results.Json.winningOption -eq 'A FAVOR' -and $results.Json.totalVotes -eq 1)

Write-Host '17) Proveedores y Contratos (Fase 5)'
$sups = Call GET '/suppliers' $admin
Check 'proveedores demo consultados' ($sups.Status -eq 200 -and $sups.Json.totalElements -ge 2)
$demoSup = $sups.Json.content[0]

$newSup = Call POST '/suppliers' $admin @{
  taxId = "SMOKE-$runId"
  name = "Jardines y Paisajismo S.A.S. $runId"
  contactName = 'Pedro Pérez'
  contactPhone = '3119998877'
  contactEmail = 'jardines@valle.local'
  category = 'JARDINERIA'
  rating = 5
  notes = 'Proveedor de mantenimiento de zonas verdes'
}
Check 'crear nuevo proveedor (201)' ($newSup.Status -eq 201 -and $newSup.Json.id)
$newSupId = $newSup.Json.id

$contracts = Call GET "/suppliers/$($demoSup.id)/contracts" $admin
Check 'contratos de proveedor demo consultados' ($contracts.Status -eq 200 -and @($contracts.Json).Count -ge 1)

$newCtr = Call POST "/suppliers/$newSupId/contracts" $admin @{
  contractNumber = "CTR-JAR-$runId"
  title = 'Mantenimiento mensual jardines'
  description = 'Poda de césped y ornato mensual'
  startDate = (Get-Date).ToString("yyyy-MM-dd")
  endDate = (Get-Date).AddMonths(6).ToString("yyyy-MM-dd")
  monthlyAmount = 450000.00
  totalAmount = 2700000.00
  hasInsurancePolicy = $true
  insurancePolicyNumber = 'POL-JAR-001'
  insuranceExpirationDate = (Get-Date).AddMonths(6).ToString("yyyy-MM-dd")
}
Check 'crear contrato de proveedor (201)' ($newCtr.Status -eq 201 -and $newCtr.Json.id)

Write-Host '18) Mantenimiento y Ordenes de Trabajo (Fase 5)'
$assets = Call GET '/maintenance/assets' $admin
Check 'activos de mantenimiento consultados' ($assets.Status -eq 200 -and @($assets.Json).Count -ge 2)
$assetElev = ($assets.Json | Where-Object { $_.category -eq 'ELEVATOR' })[0]

$plans = Call GET "/maintenance/plans?assetId=$($assetElev.id)" $admin
Check 'planes de mantenimiento consultados' ($plans.Status -eq 200 -and @($plans.Json).Count -ge 1)
$planElev = $plans.Json[0]

$orders = Call GET '/maintenance/work-orders' $admin
Check 'ordenes de trabajo consultadas' ($orders.Status -eq 200 -and $orders.Json.totalElements -ge 1)

$newWo = Call POST '/maintenance/work-orders' $admin @{
  assetId = $assetElev.id
  planId = $planElev.id
  supplierId = $demoSup.id
  type = 'PREVENTIVE'
  title = "Inspección correctiva de prueba $((Get-Random))"
  description = 'Ajuste de contactores y sensores de puerta'
  priority = 'HIGH'
  estimatedCost = 250000.00
  assignedTo = 'Técnico de Turno'
}
Check 'crear orden de trabajo (201)' ($newWo.Status -eq 201 -and $newWo.Json.id)
$woId = $newWo.Json.id

$closeWo = Call PUT "/maintenance/work-orders/$woId/status" $admin @{
  status = 'COMPLETED'
  actualCost = 245000.00
  completionNotes = 'Sensores calibrados exitosamente y en servicio normal.'
}
Check 'completar orden de trabajo con costo real (200)' ($closeWo.Status -eq 200 -and $closeWo.Json.status -eq 'COMPLETED' -and $closeWo.Json.actualCost -eq 245000.00)

Write-Host '19) Presupuesto Anual y Ejecucion (Fase 5)'
$budgets = Call GET '/budgets' $admin
Check 'presupuestos consultados' ($budgets.Status -eq 200 -and @($budgets.Json).Count -ge 1)
$b2026 = $budgets.Json[0]

$bSummary = Call GET '/budgets/2026/summary' $admin
Check 'resumen presupuestal 2026 calculado' ($bSummary.Status -eq 200 -and $bSummary.Json.totalIncomeBudgeted -gt 0 -and $bSummary.Json.totalExpenseBudgeted -gt 0)

$bItems = Call GET "/budgets/$($b2026.id)/items" $admin
Check 'rubros presupuestales consultados' ($bItems.Status -eq 200 -and @($bItems.Json).Count -ge 3)

$newItem = Call POST "/budgets/$($b2026.id)/items" $admin @{
  code = "3.$($runId.Substring(0, 6))"
  name = "Imprevistos y Fondo de Reserva $runId"
  type = 'EXPENSE'
  budgetedAmount = 5000000.00
}
Check 'crear nuevo rubro presupuestal (201)' ($newItem.Status -eq 201 -and $newItem.Json.id)
$itemId = $newItem.Json.id

$newExec = Call POST "/budgets/items/$itemId/executions" $admin @{
  amount = 150000.00
  executionDate = (Get-Date).ToString("yyyy-MM-dd")
  invoiceNumber = "FAC-IMP-$runId"
  description = 'Atención fuga imprevista cuarto de bombas'
}
Check 'registrar ejecucion presupuestal (201)' ($newExec.Status -eq 201 -and $newExec.Json.id)

$execs = Call GET "/budgets/items/$itemId/executions" $admin
Check 'ejecuciones del rubro consultadas' ($execs.Status -eq 200 -and @($execs.Json).Count -ge 1)

Write-Host '20) Reportes Consolidados y Camaras LPR (Fase 5)'
$repPort = Call GET '/reports/portfolio' $admin
Check 'reporte de cartera consolidado' ($repPort.Status -eq 200 -and @($repPort.Json.items).Count -ge 1)

$csvResp = Invoke-WebRequest -Uri "$base/reports/portfolio/export/csv" -Headers @{ Authorization = "Bearer $admin" } -UseBasicParsing
Check 'exportar reporte de cartera en CSV (200)' ($csvResp.StatusCode -eq 200 -and $csvResp.Content -like '*Inmueble;Torre;Saldo Pendiente*')

$pdfResp = Invoke-WebRequest -Uri "$base/reports/portfolio/export/pdf" -Headers @{ Authorization = "Bearer $admin" } -UseBasicParsing
$pdfMagic = [System.Text.Encoding]::ASCII.GetString($pdfResp.Content[0..3])
Check 'exportar reporte de cartera en PDF (200 %PDF)' ($pdfResp.StatusCode -eq 200 -and $pdfMagic -eq '%PDF')

$repBud = Call GET '/reports/budget/2026' $admin
Check 'reporte de presupuesto 2026' ($repBud.Status -eq 200 -and $repBud.Json.year -eq 2026)

$repMaint = Call GET '/reports/maintenance' $admin
Check 'reporte de mantenimiento' ($repMaint.Status -eq 200 -and $repMaint.Json.totalAssets -ge 2 -and $repMaint.Json.totalWorkOrders -ge 1)

$camStatus = Call GET '/cameras/status' $admin
Check 'estado de camara LPR ONLINE' ($camStatus.Status -eq 200 -and $camStatus.Json.status -eq 'ONLINE')

$camDetAuth = Call POST '/cameras/detect' $admin @{ source = 'GATE_MAIN_LPR'; plate = 'ABC123' }
Check 'deteccion LPR placa registrada autorizada' ($camDetAuth.Status -eq 200 -and $camDetAuth.Json.registered -eq $true)

$camDetUnk = Call POST '/cameras/detect' $admin @{ source = 'GATE_MAIN_LPR'; plate = 'ZZZ999' }
Check 'deteccion LPR placa desconocida visitante' ($camDetUnk.Status -eq 200 -and $camDetUnk.Json.registered -eq $false)

Write-Host '21) Autogestion de la copropiedad'
$tenant = Call GET '/tenants/current' $admin
Check 'datos de la copropiedad consultados' ($tenant.Status -eq 200 -and $tenant.Json.name)
$tenantPayload = @{
  name = $tenant.Json.name
  nit = $tenant.Json.nit
  address = $tenant.Json.address
  city = $tenant.Json.city
  department = $tenant.Json.department
  phone = $tenant.Json.phone
  email = $tenant.Json.email
}
$savedTenant = Call PUT '/tenants/current' $admin $tenantPayload
Check 'administrador actualiza datos de la copropiedad' ($savedTenant.Status -eq 200 -and $savedTenant.Json.name -eq $tenant.Json.name)
$camerasEnabled = [bool]$tenant.Json.features.CAMERAS
$featureSaved = Call PUT '/tenants/current/features/CAMERAS' $admin @{ enabled = $camerasEnabled }
Check 'administrador cambia un canal autogestionable' ($featureSaved.Status -eq 200 -and $featureSaved.Json.CAMERAS -eq $camerasEnabled)
$platformOnly = Call PUT '/tenants/current/features/ONLINE_PAYMENTS' $admin @{ enabled = $true }
Check 'pagos en linea sigue siendo de plataforma (403)' ($platformOnly.Status -eq 403)
$secretaryDenied = Call PUT '/tenants/current' $secr $tenantPayload
Check 'secretaria no edita la copropiedad (403)' ($secretaryDenied.Status -eq 403)

Write-Host '22) Nomina parametrizable'
$paySettings = Call GET '/payroll/settings' $admin
Check 'parametros de nomina consultados' ($paySettings.Status -eq 200 -and $paySettings.Json.baseDays -ge 1 -and $paySettings.Json.healthEmployeeRate -ge 0)
$payEmployees = Call GET '/payroll/employees?size=10' $admin
Check 'empleada demo activa' ($payEmployees.Status -eq 200 -and @($payEmployees.Json.content).Count -ge 1)
$payPeriod = (Get-Date).ToString('yyyy-MM')
$payRun = Call POST '/payroll/runs' $admin @{ period = $payPeriod; notes = 'Prueba de humo' }
if ($payRun.Status -eq 409) {
  $runs = Call GET '/payroll/runs?size=100' $admin
  $existingRun = $runs.Json.content | Where-Object { $_.period -eq $payPeriod } | Select-Object -First 1
  $existingStatus = if ($existingRun) { 200 } else { 404 }
  $payRun = [pscustomobject]@{ Status = $existingStatus; Json = $existingRun }
}
Check 'nomina calculada o existente' (($payRun.Status -eq 200 -or $payRun.Status -eq 201) -and $payRun.Json.id)
$payRunId = $payRun.Json.id
$payCsv = Invoke-WebRequest -Uri "$base/payroll/runs/$payRunId/export.csv" -Headers @{ Authorization = "Bearer $admin"; Accept = 'text/csv' } -UseBasicParsing
Check 'exportacion de nomina en CSV' ($payCsv.StatusCode -eq 200 -and $payCsv.Content -like '*Periodo;Documento;Empleado*')
if ($payRun.Json.status -eq 'CALCULATED') {
  $payVoid = Call POST "/payroll/runs/$payRunId/void" $admin @{ reason = 'Limpieza de prueba de humo' }
  Check 'nomina anulada para dejar limpio el periodo' ($payVoid.Status -eq 200 -and $payVoid.Json.status -eq 'VOID')
}

Write-Host '23) WhatsApp: verificacion, firma e idempotencia'
if ($envMap['WHATSAPP_ENABLED'] -ne 'true') {
  Write-Host '  SKIP WhatsApp deshabilitado en .env' -ForegroundColor Yellow
} else {
  $waVerifyToken = $envMap['WHATSAPP_VERIFY_TOKEN']
  $waAppSecret = $envMap['WHATSAPP_APP_SECRET']
  Check 'credenciales de WhatsApp configuradas' ($waVerifyToken -and $waAppSecret)
  Check 'canal WhatsApp habilitado en la copropiedad' ($tenant.Json.features.WHATSAPP -eq $true)
  if ($waVerifyToken -and $waAppSecret) {
    $challenge = "smoke-$runId"
    $verifyQuery = "hub.mode=subscribe&hub.verify_token=$([uri]::EscapeDataString($waVerifyToken))&hub.challenge=$challenge"
    $waVerify = Invoke-WebRequest -Uri "$base/public/whatsapp/webhook?$verifyQuery" -UseBasicParsing
    Check 'webhook GET responde el challenge' ($waVerify.StatusCode -eq 200 -and $waVerify.Content -eq $challenge)

    $waMsg = @{
      object = 'whatsapp_business_account'
      entry = @(@{
        id = 'smoke'
        changes = @(@{
          field = 'messages'
          value = @{
            messaging_product = 'whatsapp'
            metadata = @{ display_phone_number = 'smoke'; phone_number_id = $envMap['WHATSAPP_PHONE_NUMBER_ID'] }
            messages = @(@{
              from = '573000000002'
              id = "wamid.smoke.$runId"
              timestamp = "$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
              type = 'text'
              text = @{ body = 'Hola, necesito ayuda con una reserva' }
            })
          }
        })
      })
    }
    $waBody = $waMsg | ConvertTo-Json -Compress -Depth 12
    $waSig = 'sha256=' + (HmacSha256Hex $waBody $waAppSecret)
    $waResp = Invoke-WebRequest -Uri "$base/public/whatsapp/webhook" -Method POST -Body $waBody -ContentType 'application/json' -Headers @{ 'X-Hub-Signature-256' = $waSig } -UseBasicParsing
    Check 'webhook POST firmado procesado' ($waResp.StatusCode -eq 200)
    $waDup = Invoke-WebRequest -Uri "$base/public/whatsapp/webhook" -Method POST -Body $waBody -ContentType 'application/json' -Headers @{ 'X-Hub-Signature-256' = $waSig } -UseBasicParsing
    Check 'webhook POST idempotente' ($waDup.StatusCode -eq 200)
    try {
      $waBad = Invoke-WebRequest -Uri "$base/public/whatsapp/webhook" -Method POST -Body $waBody -ContentType 'application/json' -Headers @{ 'X-Hub-Signature-256' = 'sha256=00' } -UseBasicParsing
      $waBadStatus = [int]$waBad.StatusCode
    } catch { $waBadStatus = [int]$_.Exception.Response.StatusCode }
    Check 'webhook con firma invalida rechazado (403)' ($waBadStatus -eq 403)
  }
}

if ($fails -eq 0) { Write-Host 'TODO OK' -ForegroundColor Green } else { Write-Host "HAY $fails FALLOS" -ForegroundColor Red; exit 1 }
