#define AppName "Video Get"
#define AppVersion "0.1.0"
#define AppPublisher "Video Get"
#define AppExeName "VideoGet.exe"

[Setup]
AppId={{D2162D44-B473-45B5-8A8D-7F63C4E980B6}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={localappdata}\Programs\VideoGet
DefaultGroupName=Video Get
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=..\dist\installer
OutputBaseFilename=VideoGet-Setup-{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\{#AppExeName}
CloseApplications=yes
RestartApplications=no

[Tasks]
Name: "autostart"; Description: "登录 Windows 后自动启动 Video Get"; GroupDescription: "启动选项："; Flags: unchecked
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "快捷方式："; Flags: unchecked

[Files]
Source: "..\dist\VideoGet.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\dist\VideoGet.exe"; DestDir: "{app}"; DestName: "VideoGetNativeHost.exe"; Flags: ignoreversion
Source: "..\dist\ffmpeg\bin\ffmpeg.exe"; DestDir: "{app}\ffmpeg\bin"; Flags: ignoreversion
Source: "..\dist\ffmpeg\bin\ffprobe.exe"; DestDir: "{app}\ffmpeg\bin"; Flags: ignoreversion
Source: "..\dist\ffmpeg\LICENSE"; DestDir: "{app}\ffmpeg"; Flags: ignoreversion
Source: "..\dist\ffmpeg\README.txt"; DestDir: "{app}\ffmpeg"; Flags: ignoreversion
Source: "..\native-host\com.videoget.companion.chrome.json"; DestDir: "{app}\native-host"; Flags: ignoreversion
Source: "..\native-host\com.videoget.companion.edge.json"; DestDir: "{app}\native-host"; Flags: ignoreversion
Source: "..\THIRD_PARTY_NOTICES.md"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\Video Get"; Filename: "{app}\{#AppExeName}"
Name: "{userdesktop}\Video Get"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Registry]
Root: HKCU; Subkey: "Software\Google\Chrome\NativeMessagingHosts\com.videoget.companion"; ValueType: string; ValueName: ""; ValueData: "{app}\native-host\com.videoget.companion.chrome.json"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Microsoft\Edge\NativeMessagingHosts\com.videoget.companion"; ValueType: string; ValueName: ""; ValueData: "{app}\native-host\com.videoget.companion.edge.json"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "VideoGet"; ValueData: """{app}\{#AppExeName}"""; Tasks: autostart; Flags: uninsdeletevalue

[Run]
Filename: "{app}\{#AppExeName}"; Description: "启动 Video Get"; Flags: nowait postinstall skipifsilent

[UninstallRun]
Filename: "{cmd}"; Parameters: "/C taskkill /IM VideoGet.exe /F"; Flags: runhidden; RunOnceId: "StopVideoGet"

[Code]
procedure UpdateNativeManifest(const FileName: String);
var
  ContentAnsi: AnsiString;
  Content: String;
  HostPath: String;
begin
  if not LoadStringFromFile(FileName, ContentAnsi) then
    RaiseException('无法读取 Native Messaging 配置：' + FileName);
  Content := String(ContentAnsi);
  HostPath := ExpandConstant('{app}\VideoGetNativeHost.exe');
  StringChangeEx(HostPath, '\', '\\', True);
  StringChangeEx(Content, 'VideoGetNativeHost.exe', HostPath, True);
  if not SaveStringToFile(FileName, AnsiString(Content), False) then
    RaiseException('无法更新 Native Messaging 配置：' + FileName);
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssPostInstall then
  begin
    UpdateNativeManifest(ExpandConstant('{app}\native-host\com.videoget.companion.chrome.json'));
    UpdateNativeManifest(ExpandConstant('{app}\native-host\com.videoget.companion.edge.json'));
  end;
end;
