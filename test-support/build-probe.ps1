param([string]$JavaHome = 'C:\Program Files\Java\jdk-25')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$mavenRoot = Join-Path $env:USERPROFILE '.m2\repository'
$apiJar = Join-Path $mavenRoot 'io\papermc\paper\paper-api\26.2.build.129-stable\paper-api-26.2.build.129-stable.jar'
$dependencies = Get-ChildItem $mavenRoot -Recurse -File -Filter '*.jar' | Where-Object { $_.FullName -match 'adventure|examination|annotations|bungeecord-chat|guava|netty' }
$probeClasspath = (@($apiJar, (Join-Path $projectRoot 'target\classes')) + $dependencies.FullName) -join ';'
$classesPath = Join-Path $PSScriptRoot 'build\classes'
New-Item -ItemType Directory -Force -Path $classesPath | Out-Null
$probeSources=(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'probe') -Filter '*.java' -File).FullName
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $probeClasspath -d $classesPath $probeSources
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'probe\plugin.yml') -Destination $classesPath
& (Join-Path $JavaHome 'bin\jar.exe') --create --file (Join-Path $PSScriptRoot 'build\GuardProbe.jar') -C $classesPath .
if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed' }
