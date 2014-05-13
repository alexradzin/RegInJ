# RegInJ

Super lightweight pure java library for manipulating MS Windows registry

## Scope

This document explains motivation, API and design of RegInJ

## Motivation

Java is a cross platform language and therefore does not support platform specific features like access to MS Windows registry. 
Normally we have to use JNI or external processes (directly or indirectly) to do this.  


## Solution

JDK provides class `java.util.prefs.Preferences` that helps us to manipulate application configuration in cross platform manner. 
On windows preferences are stored in registry and the code is implemented in class `WindowsPreferences` that extends `Preferences`. 
`WindowsPreferences` manages registry entries using native methods `WindowsRegOpenKey`, `WindowsRegCloseKey`, `WindowsRegDeleteKey` *etc*.

Unfortunately `WindowsPreferences` does not allow direct access to low-level registry manipulation. It stores information under 
`HKLM\Software\Java Soft\Prefs` and `HKCR\Software\Java Soft\Prefs` only. Fortunately direct calls of low level native methods are still 
possible that allowed current implementation. 


## Library name

As it was already mentioned this library does not use any external tools but utilizes *internal* JDK classes to access *registry* using pure *java*.
So, it's name is *RegInJ*:
* *Reg* for *registry*
* *In* for *insight*
* *J* for *Java*


## Public API

The library is very small and has single entry point - class `com.reginj.Registry`.

### Instantiation
There is only one registry per operating system. Therefore there is only one instance of class `com.reginj.Registry`. 
Therefore it is designed as a sigleton. User `Registry.getInstance()` to access its API. 


### Methods

Methods are simple and have self-explainable names:
* `keys(Hive hive, String path)`
* `values(Hive hive, String path)`
* `get(Hive hive, String path, String name)`
* `createKey(Hive hive, String path)`
* `put(Hive hive, String path, String name, String value)`
* `removeKey(Hive hive, String path)`
* `removeValue(Hive hive, String path, String name)`


### Exceptions

All methods throw `java.io.IOException` and its subclasses or subclasses of `RuntimeException` provided by JDK. RegInJ does not declare its own exceptions. 
This is done to simplify introduction and extraction of the package. If someone started using it and then decided to 
replace it by other package at least there is no need to look in the code all places where exceptions specific to this 
library are caught. 

So far author found only several error codes that can be produced when using the library. The error codes and their mapping 
to exception is summarized in the following table. 

| Error 					|	Code	| Exception							| Text				| Description										|
| ------------------------- | --------- | ---------------------------------	| ----------------- | ------------------------------------------------- |
FILE_NOT_FOUND				|	2		| `FileNotFoundException` 			| Path is not found	| Key or value name does not exist
ACCESS_DENIED				|	5		| `AccessDeniedException` 			| Access denied		| This operation is forbidden for current user
ERROR_INVALID_HANDLE		|	6		| `FileNotFoundException` 			| Invalid handle	| Wrong registry hive (e.g. `HKEY_PERFORMANCE_DATA`)
ERROR_CALL_NOT_IMPLEMENTED	| 120 		| `UnsupportedOperationException`	| Not implemented 	| Wrong registry hive (e.g. `HKEY_DYN_DATA`)


## Limitations

Unfortunately class `WindowsPreferences` is not designed to work with value types other than `String`.
So, this is the limitation of current library. However hopefully this is enough for most applications. 


## Compatibility issues

### Compatibility with JRE version

Since RegInJ performs calls to not public methods of package protected class it depends on the JDK version. 
Fortunately preferences support was introduced in JDK 1.4 and have not been changed until now. So, hopefully
RegInJ will work on all JRE versions starting from 1.4. In fact it was developed with JRE 1.6 and refatrored now
with JRE 1.7 without any compatibility problems. Author will do his best effort to verify that other versions work as well.


### Compatibility with OS version

RegInJ calls native windows API, so compatibility with different versions of Windows may become and issue. However hopefully
this will work on most platforms. Author tried it on Windows XP (professional and home edition) and Windows 8.
The following compatibility problems have been found.
1. Theoretically there are 9 hives: `HKEY_CLASSES_ROOT, HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE, HKEY_USERS, HKEY_PERFORMANCE_DATA, HKEY_CURRENT_CONFIG, HKEY_DYN_DATA, HKEY_PERFORMANCE_TEXT, HKEY_PERFORMANCE_NLSTEXT`.
   However some of them are not available on all platforms. For example HKEY_DYN_DATA is implemented on Windows 9x and ME only. 
   Take a look on [this article](http://en.wikipedia.org/wiki/Windows_Registry) for more details.
2. Microsoft is constantly improves its security limitation. Some operations may work on one version of windows and throw "Access denied" on others.
   For example on Windows 8 user without administrative privileges cannot write to HKEY_LOCAL_MACHINE. Tests that worked well on Windows XP threw "Access denied" on Windows 8.


## Benifits

1. RegInJ is very small. All classes together occupy 15K and jar is 9K only. 
2. The source code is contained in only one upper level class (that includes 2 inner classes). 
   It is very easy to incorporate this class as a source code into any project if external dependencies are problematic for some reasons. 
3. RegInJ is pure java. No JNI, no external processes. It is easy to use it in client applications distributed via java web start. 


## Implementation details

The implementation is based on invocation of private native methods declared in WindowsPreferences class using reflection:

```java
private <R> R call(String methodName, Class[] types, Object[] args) {
  try {
	Method m = winPrefClazz.getDeclaredMethod(methodName, types);
	m.setAccessible(true);
	return (R)m.invoke(null, args);
  } catch (Exception e) {
	throw new RuntimeException(e);
  }
}
```


Here is the example how this method is used. This is what I would like to write: 

`int hresult = WindowsRegCloseKey(handle);`

This is what I have to do instead:

`int result = call("WindowsRegCloseKey", new Class[] {int.class}, new Object[] {handle});`

Using reflection is ugly but sometimes has some benefits. For example methods that enumerate registry keys and values are very similar but they call different native methods 
(e.g. WindowsRegEnumValue and WindowsRegEnumKeyEx). So methods childrenNamesSpi() and keysSpi() are almost duplicate in WindowsPreferences. 
Reflection allows creating one implementation for both cases.


## References

First version of this library was presented first time at January, 2011 in my [blog](http://alexradzin.blogspot.co.il/2011/01/access-windows-registry-with-pure-java.html). 
But recently I decided to publish the code at github. This process included name changes, more tests and some improvements of error handling. 


### Similar implementations

* http://lenkite.blogspot.com/2008/05/access-windows-registry-using-java.html
* http://www.davidc.net/programming/java/reading-windows-registry-java-without-jni
