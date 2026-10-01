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
$fails = 0

function Call($method, $path, $token = $null, $body = $null) {
  $h = @{}; if ($token) { $h['Authorization'] = "Bearer $token" }
  $args = @{ Uri = "$base$path"; Method = $method; Headers = $h; UseBasicParsing = $true }
  if ($body) { $args['Body'] = ($body | ConvertTo-Json -Compress); $args['ContentType'] = 'application/json' }
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

if ($fails -eq 0) { Write-Host 'TODO OK' -ForegroundColor Green } else { Write-Host "HAY $fails FALLOS" -ForegroundColor Red; exit 1 }
