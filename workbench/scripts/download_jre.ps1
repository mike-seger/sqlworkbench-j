$url = "https://www.sql-workbench.eu/jre/jre_win64.zip"

$filename = "OpenJDK.zip";

Write-Host "Downloading $filename (approx. 50MB)"

[Net.ServicePointManager]::SecurityProtocol = "tls12, tls11, tls"
Invoke-WebRequest -Uri $url -OutFile $filename

Write-Host "Extracting $filename to $PSScriptRoot"

Expand-Archive -Path $filename -DestinationPath $PSScriptRoot\jre -Force
