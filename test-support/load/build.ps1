param([string]$JavaHome = 'C:\Program Files\Java\jdk-25')
$ErrorActionPreference = 'Stop'
$apiJar = Join-Path $env:USERPROFILE '.m2\repository\io\papermc\paper\paper-api\26.2.build.129-stable\paper-api-26.2.build.129-stable.jar'
$dependencies = Get-ChildItem (Join-Path $env:USERPROFILE '.m2\repository') -Recurse -File -Filter '*.jar' | Where-Object { $_.FullName -match 'adventure|examination|annotations|bungeecord-chat|guava' }
$fixtureClasspath = (@($apiJar) + $dependencies.FullName) -join ';'
$classesPath = Join-Path $PSScriptRoot '..\build\load-classes'
New-Item -ItemType Directory -Force -Path $classesPath | Out-Null
& (Join-Path $JavaHome 'bin\javac.exe') -encoding UTF-8 -cp $fixtureClasspath -d $classesPath (Join-Path $PSScriptRoot 'LoadProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'LoadProbe compilation failed' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'plugin.yml') -Destination $classesPath
& (Join-Path $JavaHome 'bin\jar.exe') --create --file (Join-Path $PSScriptRoot '..\build\LoadProbe.jar') -C $classesPath .
if ($LASTEXITCODE -ne 0) { throw 'LoadProbe packaging failed' }
