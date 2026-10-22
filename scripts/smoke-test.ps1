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

Write-Host '24) Mascotas por inmueble'
$petAdmin = Call POST '/pets' $admin @{ name = "Mascota Smoke $runId"; species = 'GATO'; unitId = $unit101; vaccinated = $true; vaccineExpiresOn = (Get-Date).AddMonths(6).ToString('yyyy-MM-dd'); microchip = "SMOKE$runId" }
Check 'administrador registra mascota (201)' ($petAdmin.Status -eq 201 -and $petAdmin.Json.id)
$petDup = Call POST '/pets' $admin @{ name = "Mascota Duplicada $runId"; species = 'PERRO'; unitId = $unit101; microchip = "SMOKE$runId" }
Check 'microchip duplicado (409)' ($petDup.Status -eq 409 -and $petDup.Json.code -eq 'PET_MICROCHIP_ALREADY_REGISTERED')
$petSearch = Call GET "/pets?q=SMOKE$runId" $admin
Check 'busqueda de mascota por microchip' ($petSearch.Status -eq 200 -and $petSearch.Json.totalElements -eq 1)
$petMine = Call GET "/my/pets?unitId=$unit101" $ot
Check 'propietario ve la mascota de su inmueble' ($petMine.Status -eq 200 -and @($petMine.Json | Where-Object { $_.id -eq $petAdmin.Json.id }).Count -eq 1)
$petResident = Call POST '/my/pets' $ot @{ name = "Mascota Residente $runId"; species = 'PERRO'; unitId = $unit101 }
Check 'propietario registra mascota propia (201)' ($petResident.Status -eq 201 -and $petResident.Json.id)
$surUnit = @((Call GET '/my/units' $ots).Json)[0].unitId
$petForeign = Call POST '/my/pets' $ot @{ name = "Mascota Ajena $runId"; species = 'AVE'; unitId = $surUnit }
Check 'propietario no registra en inmueble de otra copropiedad (404)' ($petForeign.Status -eq 404)
Check 'portero consulta mascotas (200)' ((Call GET '/pets?size=5' $guard).Status -eq 200)
Check 'portero no registra mascotas (403)' ((Call POST '/pets' $guard @{ name = 'No permitido'; species = 'OTRO'; unitId = $unit101 }).Status -eq 403)
Check 'propietario no accede al registro administrativo (403)' ((Call GET '/pets' $ot).Status -eq 403)
Check 'propietario elimina su mascota' ((Call DELETE "/my/pets/$($petResident.Json.id)" $ot).Status -eq 204)
Check 'administrador elimina la mascota de prueba' ((Call DELETE "/pets/$($petAdmin.Json.id)" $admin).Status -eq 204)
$petGone = Call GET "/pets?q=SMOKE$runId" $admin
Check 'mascota eliminada no aparece en el registro' ($petGone.Status -eq 200 -and $petGone.Json.totalElements -eq 0)

Write-Host '25) Junta directiva'
$board = Call GET '/board' $admin
Check 'junta directiva demo (3 integrantes)' ($board.Status -eq 200 -and @($board.Json).Count -ge 3)
$boardPresident = @($board.Json | Where-Object { $_.position -eq 'PRESIDENTE' })[0]
$boardOriginalNotes = $boardPresident.notes
if ($null -eq $boardOriginalNotes) { $boardOriginalNotes = '' }
$boardStartDate = ([string]$boardPresident.startDate).Substring(0, 10)
$boardUpd = Call PUT "/board/$($boardPresident.id)" $admin @{
  personId = $boardPresident.personId
  position = $boardPresident.position
  startDate = $boardStartDate
  endDate = $null
  status = 'ACTIVE'
  notes = "Nota de prueba $runId"
}
Check 'actualizacion de miembro de junta (200)' ($boardUpd.Status -eq 200 -and $boardUpd.Json.notes -eq "Nota de prueba $runId")
$boardDup = Call POST '/board' $admin @{ personId = $boardPresident.personId; position = 'VOCAL'; startDate = (Get-Date).ToString('yyyy-MM-dd'); status = 'ACTIVE' }
Check 'persona con cargo activo duplicado (409)' ($boardDup.Status -eq 409)
$boardRestore = Call PUT "/board/$($boardPresident.id)" $admin @{
  personId = $boardPresident.personId
  position = $boardPresident.position
  startDate = $boardStartDate
  endDate = $null
  status = 'ACTIVE'
  notes = $boardOriginalNotes
}
Check 'nota original restaurada' ($boardRestore.Status -eq 200)
Check 'propietario consulta la junta activa' (@((Call GET '/board?status=ACTIVE' $ot).Json).Count -ge 1)

Write-Host '26) Citas con la administracion'
$apptAdmin = Call GET '/appointments?size=5' $admin
Check 'agenda administrativa consultada' ($apptAdmin.Status -eq 200 -and $apptAdmin.Json.totalElements -ge 1)
$apptMine = Call GET '/my/appointments' $ot
Check 'propietario consulta sus citas' ($apptMine.Status -eq 200 -and @($apptMine.Json).Count -ge 1)
$apptDay = (Get-Date).AddDays(20 + (Get-Random -Maximum 25)).Date.AddHours(10).AddMinutes(30)
while ($apptDay.DayOfWeek -eq 'Saturday' -or $apptDay.DayOfWeek -eq 'Sunday') { $apptDay = $apptDay.AddDays(1) }
$apptStart = $apptDay.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$apptBody = @{
  unitId = $unit101
  subject = "Cita de prueba $runId"
  reason = 'Solicitud creada por la prueba de humo para validar la agenda.'
  scheduledAt = $apptStart
  durationMinutes = 30
  modality = 'VIRTUAL'
  meetingLink = 'https://meet.example.com/cita-smoke'
}
$apptNew = Call POST '/my/appointments' $ot $apptBody
Check 'solicitud de cita (201, REQUESTED)' ($apptNew.Status -eq 201 -and $apptNew.Json.status -eq 'REQUESTED')
$apptId = $apptNew.Json.id
$apptDup = Call POST '/my/appointments' $ot $apptBody
Check 'horario ocupado rechazado (409)' ($apptDup.Status -eq 409 -and $apptDup.Json.code -eq 'APPOINTMENT_SLOT_TAKEN')
$apptConfirm = Call PUT "/appointments/$apptId" $admin @{ status = 'CONFIRMED' }
Check 'administracion confirma la cita (200, CONFIRMED)' ($apptConfirm.Status -eq 200 -and $apptConfirm.Json.status -eq 'CONFIRMED')
$apptCancel = Call POST "/my/appointments/$apptId/cancel" $ot @{ reason = 'Cancelacion de prueba de humo' }
Check 'propietario cancela la cita (204)' ($apptCancel.Status -eq 204)
$apptAfter = Call GET '/my/appointments' $ot
$apptCancelled = @($apptAfter.Json | Where-Object { $_.id -eq $apptId })[0]
Check 'cita cancelada visible para el propietario' ($apptCancelled.status -eq 'CANCELLED')
Check 'eliminacion de cita de prueba (204)' ((Call DELETE "/appointments/$apptId" $admin).Status -eq 204)
$saturday = (Get-Date).Date.AddDays(1)
while ($saturday.DayOfWeek -ne 'Saturday') { $saturday = $saturday.AddDays(1) }
$apptWeekend = Call POST '/my/appointments' $ot @{
  unitId = $unit101
  subject = "Cita fin de semana $runId"
  reason = 'Validacion de horario no permitido.'
  scheduledAt = $saturday.AddHours(10).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
  durationMinutes = 30
  modality = 'IN_PERSON'
}
Check 'cita en sabado rechazada (400)' ($apptWeekend.Status -eq 400 -and $apptWeekend.Json.code -eq 'APPOINTMENT_OUTSIDE_HOURS')

Write-Host '27) Capacitaciones comunitarias'
$trainingList = Call GET '/trainings?size=10' $admin
Check 'capacitaciones demo consultadas' ($trainingList.Status -eq 200 -and $trainingList.Json.totalElements -ge 1)
$trainingDay = (Get-Date).AddDays(35).Date.AddHours(9)
while ($trainingDay.DayOfWeek -eq 'Saturday' -or $trainingDay.DayOfWeek -eq 'Sunday') { $trainingDay = $trainingDay.AddDays(1) }
$trainingNew = Call POST '/trainings' $admin @{
  title = "Capacitacion smoke $runId"
  description = 'Taller de prueba para validar inscripciones, cupos y asistencia.'
  trainerName = 'Facilitador de prueba'
  modality = 'HYBRID'
  location = 'Salon comunal'
  meetingLink = 'https://meet.example.com/capacitacion-smoke'
  startsAt = $trainingDay.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
  endsAt = $trainingDay.AddHours(2).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
  capacity = 10
}
Check 'capacitacion creada (201)' ($trainingNew.Status -eq 201 -and $trainingNew.Json.id)
$trainingId = $trainingNew.Json.id
$trainingMine = @((Call GET '/my/trainings' $ot).Json | Where-Object { $_.session.id -eq $trainingId })[0]
Check 'capacitacion visible para el propietario' ($trainingMine.session.id -eq $trainingId)
$enroll = Call POST "/my/trainings/$trainingId/enroll" $ot
Check 'propietario se inscribe (200, cupo 1)' ($enroll.Status -eq 200 -and $enroll.Json.enrolledCount -eq 1)
$enrollDup = Call POST "/my/trainings/$trainingId/enroll" $ot
Check 'inscripcion duplicada rechazada (409)' ($enrollDup.Status -eq 409 -and $enrollDup.Json.code -eq 'TRAINING_ALREADY_ENROLLED')
$enrollments = Call GET "/trainings/$trainingId/enrollments" $admin
Check 'administracion consulta inscripciones' ($enrollments.Status -eq 200 -and @($enrollments.Json).Count -eq 1)
$enrollmentId = @($enrollments.Json)[0].id
Check 'propietario cancela su inscripcion (204)' ((Call DELETE "/my/trainings/$trainingId/enroll" $ot).Status -eq 204)
Check 'propietario se vuelve a inscribir (200)' ((Call POST "/my/trainings/$trainingId/enroll" $ot).Status -eq 200)
$attendance = Call PUT "/trainings/enrollments/$enrollmentId" $admin @{ status = 'ATTENDED' }
Check 'asistencia registrada (ATTENDED)' ($attendance.Status -eq 200 -and $attendance.Json.status -eq 'ATTENDED')
$attendanceAbsent = Call PUT "/trainings/enrollments/$enrollmentId" $admin @{ status = 'ABSENT' }
Check 'asistencia corregida (ABSENT)' ($attendanceAbsent.Status -eq 200 -and $attendanceAbsent.Json.status -eq 'ABSENT')
Check 'cancelacion de la capacitacion (204)' ((Call POST "/trainings/$trainingId/cancel" $admin).Status -eq 204)
Check 'eliminacion de la capacitacion de prueba (204)' ((Call DELETE "/trainings/$trainingId" $admin).Status -eq 204)
Check 'propietario no administra capacitaciones (403)' ((Call POST '/trainings' $ot @{ title = 'No permitido'; startsAt = $trainingDay.ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'); endsAt = $trainingDay.AddHours(1).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ'); capacity = 5 }).Status -eq 403)

Write-Host '28) Personal operativo'
$staffList = Call GET '/staff?size=10' $admin
Check 'directorio de personal demo' ($staffList.Status -eq 200 -and $staffList.Json.totalElements -ge 1)
$staffNew = Call POST '/staff' $admin @{
  fullName = "Persona Smoke $runId"
  documentType = 'CC'
  documentNumber = "SMOKE-$runId"
  position = 'Auxiliar de servicios'
  area = 'Servicios generales'
  phone = '+57 300 000 0000'
  email = "smoke-$runId@demo.local"
  shift = 'Lunes a viernes'
  startDate = (Get-Date).ToString('yyyy-MM-dd')
  status = 'ACTIVE'
}
Check 'personal registrado (201)' ($staffNew.Status -eq 201 -and $staffNew.Json.id)
$staffId = $staffNew.Json.id
$staffUpd = Call PUT "/staff/$staffId" $admin @{
  fullName = "Persona Smoke Actualizada $runId"
  documentType = 'CC'
  documentNumber = "SMOKE-$runId"
  position = 'Auxiliar de servicios'
  area = 'Servicios generales'
  phone = '+57 300 111 1111'
  email = "smoke-$runId@demo.local"
  shift = 'Lunes a sabado'
  startDate = (Get-Date).ToString('yyyy-MM-dd')
  status = 'ACTIVE'
}
Check 'personal actualizado (200)' ($staffUpd.Status -eq 200 -and $staffUpd.Json.fullName -like '*Actualizada*')
$staffDup = Call POST '/staff' $admin @{ fullName = 'Documento duplicado'; documentType = 'CC'; documentNumber = "SMOKE-$runId"; position = 'Auxiliar'; startDate = (Get-Date).ToString('yyyy-MM-dd') }
Check 'documento de personal duplicado (409)' ($staffDup.Status -eq 409)
Check 'portero consulta personal (200)' ((Call GET '/staff?size=5' $guard).Status -eq 200)
Check 'portero no administra personal (403)' ((Call POST '/staff' $guard @{ fullName = 'No permitido'; documentNumber = 'NO'; position = 'Ninguno'; startDate = (Get-Date).ToString('yyyy-MM-dd') }).Status -eq 403)
Check 'personal de prueba eliminado (204)' ((Call DELETE "/staff/$staffId" $admin).Status -eq 204)

Write-Host '29) Expediente del inmueble'
$unitFile = Call GET "/properties/$unit101/file" $admin
Check 'expediente consultado (200, relaciones y notas)' ($unitFile.Status -eq 200 -and @($unitFile.Json.relations).Count -ge 1 -and @($unitFile.Json.notes).Count -ge 1)
$noteNew = Call POST "/properties/$unit101/notes" $admin @{
  title = "Nota interna smoke $runId"
  body = 'Seguimiento interno creado por la prueba de humo.'
  internal = $true
}
Check 'nota interna creada (201)' ($noteNew.Status -eq 201 -and $noteNew.Json.internal -eq $true)
$noteId = $noteNew.Json.id
$ownerFileInternal = Call GET "/my/units/$unit101/file" $ot
Check 'residente no ve la nota interna' (@($ownerFileInternal.Json.notes | Where-Object { $_.id -eq $noteId }).Count -eq 0)
$noteUpd = Call PUT "/properties/$unit101/notes/$noteId" $admin @{
  title = "Nota publica smoke $runId"
  body = 'Nota convertida en publica para validar la visibilidad del residente.'
  internal = $false
}
Check 'nota convertida en publica (200)' ($noteUpd.Status -eq 200 -and $noteUpd.Json.internal -eq $false)
$ownerFilePublic = Call GET "/my/units/$unit101/file" $ot
Check 'residente ve la nota publica' (@($ownerFilePublic.Json.notes | Where-Object { $_.id -eq $noteId }).Count -eq 1)
Check 'residente no crea notas (403)' ((Call POST "/properties/$unit101/notes" $ot @{ title = 'No permitido'; body = 'No permitido'; internal = $false }).Status -eq 403)
$unitDocTmp = Join-Path $env:TEMP "expediente-smoke-$runId.txt"
"DOCUMENTO DEL EXPEDIENTE $runId" | Out-File -FilePath $unitDocTmp -Encoding utf8
$unitDoc = & curl.exe -s -F "file=@$unitDocTmp" -F "title=Documento expediente $runId" -F "category=OTRO" -F "minimumRole=RESIDENTE" -F "unitId=$unit101" -H "Authorization: Bearer $admin" "$base/documents" | ConvertFrom-Json
Check 'documento asociado al inmueble (201)' ($unitDoc.id -and $unitDoc.unitId -eq $unit101)
$unitFileWithDoc = Call GET "/properties/$unit101/file" $admin
Check 'documento aparece en el expediente' (@($unitFileWithDoc.Json.documents | Where-Object { $_.id -eq $unitDoc.id }).Count -eq 1)
$ownerFileWithDoc = Call GET "/my/units/$unit101/file" $ot
Check 'propietario ve el documento del inmueble' (@($ownerFileWithDoc.Json.documents | Where-Object { $_.id -eq $unitDoc.id }).Count -eq 1)
$unitDocDown = Invoke-WebRequest -Uri "$base/my/documents/$($unitDoc.id)/download" -Headers @{ Authorization = "Bearer $ot" } -UseBasicParsing
Check 'propietario descarga el documento del expediente' ($unitDocDown.StatusCode -eq 200 -and $unitDocDown.Content -like '*EXPEDIENTE*')
Check 'eliminacion del documento de prueba (204)' ((Call DELETE "/documents/$($unitDoc.id)" $admin).Status -eq 204)
Check 'eliminacion de la nota de prueba (204)' ((Call DELETE "/properties/$unit101/notes/$noteId" $admin).Status -eq 204)
Remove-Item $unitDocTmp -ErrorAction SilentlyContinue
$myUnitIds = @($mine.Json | ForEach-Object { $_.unitId })
$otherUnit = @((Call GET '/properties?size=200' $admin).Json.content | Where-Object { $myUnitIds -notcontains $_.id })[0].id
Check 'propietario no consulta expediente ajeno (403)' ((Call GET "/properties/$otherUnit/file" $ot).Status -eq 403)
Check 'portero no consulta expedientes (403)' ((Call GET "/properties/$unit101/file" $guard).Status -eq 403)

Write-Host '30) Matriz de roles y permisos'
$roles = Call GET '/roles' $admin
$permissionCatalog = Call GET '/roles/permissions' $admin
Check 'roles de la copropiedad consultados' ($roles.Status -eq 200 -and @($roles.Json).Count -ge 10)
Check 'catalogo de permisos consultado (97)' ($permissionCatalog.Status -eq 200 -and @($permissionCatalog.Json).Count -ge 90)
$residentRole = @($roles.Json | Where-Object { $_.role -eq 'RESIDENTE' })[0]
Check 'residente tiene BOARD_VIEW base' (@($residentRole.permissions) -contains 'BOARD_VIEW')
$revoke = Call PUT '/roles/RESIDENTE/permissions/BOARD_VIEW' $admin @{ granted = $false }
Check 'permiso retirado en la matriz (200)' ($revoke.Status -eq 200 -and @($revoke.Json.permissions) -notcontains 'BOARD_VIEW')
$resetOne = Call DELETE '/roles/RESIDENTE/permissions/BOARD_VIEW' $admin
Check 'permiso restablecido a su valor base' ($resetOne.Status -eq 200 -and @($resetOne.Json.permissions) -contains 'BOARD_VIEW')
$revokeAll = Call PUT '/roles/RESIDENTE/permissions/BOARD_VIEW' $admin @{ granted = $false }
Check 'permiso retirado para restablecer rol' ($revokeAll.Status -eq 200)
$resetRole = Call DELETE '/roles/RESIDENTE/permissions' $admin
Check 'todos los permisos del rol restablecidos' ($resetRole.Status -eq 200 -and @($resetRole.Json.permissions) -contains 'BOARD_VIEW')
$adminReset = Call DELETE '/roles/ADMINISTRADOR/permissions' $admin
Check 'rol administrador no se puede restablecer (409)' ($adminReset.Status -eq 409)
Check 'propietario no administra roles (403)' ((Call GET '/roles' $ot).Status -eq 403)

if ($fails -eq 0) { Write-Host 'TODO OK' -ForegroundColor Green } else { Write-Host "HAY $fails FALLOS" -ForegroundColor Red; exit 1 }
