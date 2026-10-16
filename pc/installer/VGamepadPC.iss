; Inno Setup script: turns the deployed Windows build into one Setup executable.
;
; The build job runs `cmake --install`, which puts the executable, the Qt DLLs
; windeployqt copied beside it and the bundled ViGEmBus installer in one folder.
; That folder is packed here, so what a user downloads is a single .exe to run
; rather than an archive to extract first.
;
; The install is per user (%LOCALAPPDATA%) and needs no administrator rights,
; which matches the app: creating the virtual pad does not need elevation either.
; The ViGEmBus driver, when it is missing, is installed by the app through its
; own prompt, because that step is the one that needs consent.
;
; SourceDir, AppVersion and OutputDir are given by the build job on the command
; line; the values here are for running ISCC by hand.

#ifndef SourceDir
  #define SourceDir "..\dist"
#endif
#ifndef OutputDir
  #define OutputDir "..\installer-out"
#endif
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif

[Setup]
AppId={{6DE3A9F1-5C2B-4A6E-8F31-9B7C0D5A2E14}
AppName=VirtualGamePad Extended
AppVersion={#AppVersion}
AppVerName=VirtualGamePad Extended {#AppVersion}
AppPublisher=firefly-sylestia
AppPublisherURL=https://github.com/firefly-sylestia/VirtualGamePad-Extended
AppSupportURL=https://github.com/firefly-sylestia/VirtualGamePad-Extended/issues
AppUpdatesURL=https://github.com/firefly-sylestia/VirtualGamePad-Extended/releases
DefaultDirName={localappdata}\Programs\VirtualGamePad Extended
DefaultGroupName=VirtualGamePad Extended
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
OutputDir={#OutputDir}
OutputBaseFilename=VirtualGamePad-Extended-Setup-{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
UninstallDisplayIcon={app}\VGamepadPC.exe
; The app is replaced by this installer, so it has to be closed first; Windows'
; restart manager closes it rather than failing on a locked file.
CloseApplications=yes
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; A previous copy is overwritten in place: the settings file this app keeps
; beside itself, and the driver installer the app uses, both survive an update.
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\VirtualGamePad Extended"; Filename: "{app}\VGamepadPC.exe"; WorkingDir: "{app}"
Name: "{group}\{cm:UninstallProgram,VirtualGamePad Extended}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\VirtualGamePad Extended"; Filename: "{app}\VGamepadPC.exe"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\VGamepadPC.exe"; WorkingDir: "{app}"; Description: "{cm:LaunchProgram,VirtualGamePad Extended}"; Flags: nowait postinstall skipifsilent
