# 库钥 DBVault — R8 规则
# 第三方驱动全部保留：它们靠 SPI / Class.forName 加载，混淆即失效
-keep class org.mariadb.jdbc.** { *; }
-keep class org.postgresql.** { *; }
-keep class com.microsoft.sqlserver.jdbc.** { *; }
-keep class com.healthmarketscience.** { *; }
-keep class org.apache.commons.lang3.** { *; }
-keep class org.apache.commons.logging.** { *; }

# JDBC Driver 接口实现
-keep class * implements java.sql.Driver { *; }

# 应用入口与界面（Activity 名由 Manifest 引用）
-keep class com.dbvault.app.App { *; }
-keep class com.dbvault.app.ui.** { *; }

-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# 驱动依赖的可选包，Android 上不存在，屏蔽警告而不是报错
-dontwarn javax.naming.**
-dontwarn javax.management.**
-dontwarn java.lang.management.**
-dontwarn javax.transaction.**
-dontwarn javax.security.auth.**
-dontwarn org.slf4j.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.osgi.**
-dontwarn com.microsoft.**
-dontwarn org.apache.**

-dontwarn org.ietf.jgss.**
-dontwarn waffle.**
-dontwarn org.checkerframework.**
# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.amazonaws.auth.AWSCredentials
-dontwarn com.amazonaws.auth.AWSCredentialsProvider
-dontwarn com.amazonaws.auth.AWSStaticCredentialsProvider
-dontwarn com.amazonaws.auth.BasicAWSCredentials
-dontwarn com.amazonaws.auth.DefaultAWSCredentialsProviderChain
-dontwarn com.amazonaws.regions.DefaultAwsRegionProviderChain
-dontwarn com.amazonaws.services.rds.auth.GetIamAuthTokenRequest$Builder
-dontwarn com.amazonaws.services.rds.auth.GetIamAuthTokenRequest
-dontwarn com.amazonaws.services.rds.auth.RdsIamAuthTokenGenerator$Builder
-dontwarn com.amazonaws.services.rds.auth.RdsIamAuthTokenGenerator
-dontwarn com.sun.jna.FunctionMapper
-dontwarn com.sun.jna.LastErrorException
-dontwarn com.sun.jna.Library
-dontwarn com.sun.jna.Native
-dontwarn com.sun.jna.Platform
-dontwarn com.sun.jna.Pointer
-dontwarn com.sun.jna.Structure
-dontwarn com.sun.jna.TypeMapper
-dontwarn com.sun.jna.WString
-dontwarn com.sun.jna.platform.win32.BaseTSD$SIZE_T
-dontwarn com.sun.jna.platform.win32.Kernel32
-dontwarn com.sun.jna.platform.win32.Sspi$CtxtHandle
-dontwarn com.sun.jna.platform.win32.Sspi$SecBufferDesc
-dontwarn com.sun.jna.platform.win32.SspiUtil$ManagedSecBufferDesc
-dontwarn com.sun.jna.platform.win32.Win32Exception
-dontwarn com.sun.jna.platform.win32.WinNT$HANDLE
-dontwarn com.sun.jna.ptr.IntByReference
-dontwarn com.sun.jna.ptr.PointerByReference
-dontwarn com.sun.jna.win32.StdCallLibrary
-dontwarn com.sun.jna.win32.W32APIFunctionMapper
-dontwarn com.sun.jna.win32.W32APITypeMapper
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn java.awt.Point
-dontwarn java.sql.DriverAction
-dontwarn java.sql.SQLType
-dontwarn javax.script.Bindings
-dontwarn javax.script.SimpleBindings
-dontwarn javax.security.sasl.SaslException
-dontwarn javax.sql.XAConnection
-dontwarn javax.sql.XADataSource
-dontwarn javax.sql.rowset.serial.SerialException
-dontwarn javax.xml.stream.XMLInputFactory
-dontwarn javax.xml.stream.XMLOutputFactory
-dontwarn javax.xml.stream.XMLStreamException
-dontwarn javax.xml.stream.XMLStreamReader
-dontwarn javax.xml.stream.XMLStreamWriter
-dontwarn javax.xml.transform.stax.StAXResult
-dontwarn javax.xml.transform.stax.StAXSource