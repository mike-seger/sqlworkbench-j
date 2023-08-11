function Get-RedirectedUrl
{
    Param (
        [Parameter(Mandatory=$true)]
        [String]$URL
    )

    $request = [System.Net.WebRequest]::Create($url)
    $request.AllowAutoRedirect=$false
    $response=$request.GetResponse()

    $successStatus =@("Found","TemporaryRedirect","PermanentRedirect","Redirect","MovedPermanently","Moved")

    If ($successStatus -Contains $response.StatusCode)
    {
        $response.GetResponseHeader("Location")
    }
}
