package com.reginj;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.AccessDeniedException;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * This class allows access to MS Windows registry system. It uses native methods declared in {@link java.util.prefs.WindowsPreferences}. 
 * Unfortunately class {@code WindowsPreferences} does not implement whole functionality of windows registry API, so this implementation 
 * also has the following limitations: it allows access to string values only. So, it cannot be used as a fully featured facade for 
 * registry manipulation but can be very useful for most applications due to the most of registry values contain strings.
 * 
 * This class is a singleton and should be accessed using {@code static} {@link #getRegistry()} method. All public methods have self explainable
 * names and obvious usage. The methods throw {@link IOException} or its subclasses.
 *    
 * @author AlexR
 */
public class Registry {
	public static enum Hive {
	    HKEY_CLASSES_ROOT(0x80000000),
	    HKEY_CURRENT_USER(0x80000001),
	    HKEY_LOCAL_MACHINE(0x80000002),
	    HKEY_USERS(0x80000003),
	    HKEY_PERFORMANCE_DATA(0x80000004),
	    HKEY_CURRENT_CONFIG(0x80000005),
	    HKEY_DYN_DATA(0x80000006),
	    HKEY_PERFORMANCE_TEXT(0x80000050),
	    HKEY_PERFORMANCE_NLSTEXT(0x80000060),
	    ;
	    
	    private final int id;
	    
	    private Hive(int id) {
	    	this.id = id;
	    }
	    
	    int id() {
	    	return id;
	    }
	}

	
	public static enum Status {
		SUCCESS(0, "OK", null),
		FILE_NOT_FOUND(2, "Path is not found", FileNotFoundException.class),
		ACCESS_DENIED(5, "Access denied", AccessDeniedException.class),
		ERROR_INVALID_HANDLE(6, "Invalid handle", FileNotFoundException.class),
		ERROR_CALL_NOT_IMPLEMENTED(120, "Not implemented", UnsupportedOperationException.class);
		;
		
		
		private final int code;
		private final String description;
		private final Class<? extends Exception> exceptionType; 

		private static Map<Integer, Status> code2error = new HashMap<Integer, Status>();
		static {
			for (Status status : Status.values()) {
				code2error.put(status.code(), status);
			}
		}
		
		
		private Status(int code, String description, Class<? extends Exception> exceptionType) {
			this.code = code;
			this.description = description;
			this.exceptionType = exceptionType;
		}
		
		public int code() {
			return code;
		}
		
		public String hexCode() {
			return Integer.toHexString(code);
		}
		
		public String description() {
			return description;
		}


		public static Status fromCode(int code) {
			Status error =  code2error.get(code);
			if (error == null) {
				throw new IllegalArgumentException("Unknown error " + code);
			}
			return error;
		}


		@SuppressWarnings("unchecked")
		public static void assertStatus(int code, String msg) throws IOException {
	    	Status status = code2error.get(code);
	    	Class<? extends Exception> eClazz = IOException.class;
	    	String message = msg;
	    	if (status != null) {
	    		eClazz = status.exceptionType;
	    		message = msg + ": " + status.description;
	    	}
	    	message += " (" + code + ", 0x" + Integer.toHexString(code) + ")";
	    	if (eClazz == null) {
	    		return; // no exception - nothing to throw 
	    	}
	    
	    	
			try {
				if (IOException.class.isAssignableFrom(eClazz)) {
					throw ((Class<? extends IOException>)eClazz).getConstructor(String.class).newInstance(message);
				} 
				if (RuntimeException.class.isAssignableFrom(eClazz)) {
					throw ((Class<? extends RuntimeException>)eClazz).getConstructor(String.class).newInstance(message);
				} 
			} catch (ReflectiveOperationException e1) {
				throw new IllegalStateException(message, e1);
			}
	    }
		
		
		@Override
		public String toString() {
			return name() + "(" + hexCode() + "): " + description();
		}
		
	}
	
	
    
    /* Constants used to interpret returns of native functions    */
    private static final int NATIVE_HANDLE = 0;
    private static final int ERROR_CODE = 1;
    private static final int NULL_NATIVE_HANDLE = 0;
    
    /* Windows security masks */
    private static final int DELETE = 0x10000;
    private static final int KEY_QUERY_VALUE = 1;
    private static final int KEY_SET_VALUE = 2;
    private static final int KEY_CREATE_SUB_KEY = 4;
    private static final int KEY_ENUMERATE_SUB_KEYS = 8;
	
	
	
	private Class<? extends Preferences> winPrefClazz;
	private static Registry instance;
	
	@SuppressWarnings("unchecked")
	private Registry() {
		try {
			winPrefClazz = (Class<? extends Preferences>)Class.forName("java.util.prefs.WindowsPreferences");
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}
	

	public static synchronized Registry getRegistry() {
		if (instance == null) {
			instance = new Registry();
		}
		return instance;
	}
	

	public String[] keys(Hive hive, String path) throws IOException {
		return enumerate(hive, path, KEY_ENUMERATE_SUB_KEYS| KEY_QUERY_VALUE, 3, 0, "WindowsRegEnumKeyEx1");
	}
	
	public String[] values(Hive hive, String path) throws IOException {
		return enumerate(hive, path, KEY_QUERY_VALUE, 4, 2, "WindowsRegEnumValue1");
	}

	
	private String[] enumerate(Hive hive, String path, int openKeyFlags, int maxLengthIndex, int numberIndex, String queryElemMethod) throws IOException {
		int i = openKey(hive, path, openKeyFlags);
		if (i == 0) {
			throw new IOException(
					"Could not open windowsregistry node " + path);
		}
		int[] result = call("WindowsRegQueryInfoKey1",
				new Class[] { int.class }, new Object[] { i });
		if (result[1] != 0) {
			throw new IOException(
					"Could not query windowsregistry node " + path);
		}
		int maxElementNameLength = result[maxLengthIndex];
		int elementsNumber = result[numberIndex];

		try {
			String[] valueNames = new String[elementsNumber];
			for (int l = 0; l < elementsNumber; ++l) {
				byte[] windowsName = call(queryElemMethod,
						new Class[] { int.class, int.class, int.class },
						new Object[] { i, l, maxElementNameLength + 1 });
				if (windowsName == null) {
					throw new IOException(
							"Could not enumerate value #" + l + "  of windows node " + path);
				}
				valueNames[l] = call("toJavaName",
						new Class[] { byte[].class }, new Object[] { windowsName });
			}
			return valueNames;
		} finally {
			closeKey(i);
		}
	}
	
	
	public String get(Hive hive, String path, String name) throws IOException {
		int handle = openKey(hive, path, 1);
		if (handle == 0) {
			return null;
		}
		try {
			byte[] arrayOfByte = call("WindowsRegQueryValueEx",
					new Class[] {int.class, byte[].class}, 
					new Object[] { handle, toWindowsName(name) });
			return arrayOfByte == null ? 
					null : 
					this.<String>call(
						"byteArrayToString", new Class[] { byte[].class },
						new Object[] { arrayOfByte });
		} finally {
			closeKey(handle);
		}
	}

	
	public void createKey(Hive hive, String path) throws IOException {
		String[] parts = splitPath(path);
		String parent = parts[0];
		String name = parts[1];
		
	    int parentNativeHandle = openKey(hive, parent, KEY_CREATE_SUB_KEY);
	    try {
		    if (parentNativeHandle == NULL_NATIVE_HANDLE) {
		        throw new IOException("Cannot open key: " + hive + "\\" + path);
		    }
		    
			int[] result = call("WindowsRegCreateKeyEx1",
					new Class[] {int.class, byte[].class}, 
					new Object[] { parentNativeHandle, toBytes(name) });
			
			Status.assertStatus(result[ERROR_CODE], "Cannot create key: " + hive + "\\" + path);
	    } finally {
	    	closeKey(parentNativeHandle);
	    }
	}
	
	public void put(Hive hive, String path, String name, String value) throws IOException {
	    int nativeHandle = openKey(hive, path, KEY_SET_VALUE);
	    if (nativeHandle == NULL_NATIVE_HANDLE) {
	        throw new IOException("Cannot open key: " + hive.name() + "\\" + path);
	    }
	    //int result =  WindowsRegSetValueEx1(nativeHandle, toWindowsName(name), toBytes(value));
		int result = call("WindowsRegSetValueEx1",
				new Class[] {int.class, byte[].class, byte[].class}, 
				new Object[] { nativeHandle, toWindowsName(name), toBytes(value) });
	    closeKey(nativeHandle);
	    
	    Status.assertStatus(result, "Could not assign value to key " + hive.name() + "\\" + path);
	}
	
	public void removeKey(Hive hive, String path) throws IOException {
		String[] parts = splitPath(path);
		String parentPath = parts[0];
		String keyName = parts[1];
		
		remove(hive, parentPath, keyName, DELETE, "WindowsRegDeleteKey", "key");
	}
	
	public void removeValue(Hive hive, String path, String name) throws IOException {
		remove(hive, path, name, KEY_SET_VALUE, "WindowsRegDeleteValue", "value");
	}

	
	private void remove(Hive hive, String path, String name, int flag, String deleteMethod, String entityType) throws IOException {
		int nativeHandle = openKey(hive, path, flag);
		try {
	        if (nativeHandle == NULL_NATIVE_HANDLE) {
	        	return;
	        }
	        //int result = WindowsRegDeleteValue(nativeHandle, toWindowsName(key));
			int result = call(deleteMethod,
					new Class[] {int.class, byte[].class}, 
					new Object[] { nativeHandle, toWindowsName(name)});
	        
	        Status.assertStatus(result, "Could not delete " + entityType + " from windows registry " + hive.name() + "\\" + path);
		} finally {
			closeKey(nativeHandle);
		}
	}
	

    private byte[] toWindowsName(String javaName) {
        StringBuffer windowsName = new StringBuffer();
        for (int i = 0; i < javaName.length(); i++) {
            char ch =javaName.charAt(i);
            if ((ch < 0x0020)||(ch > 0x007f)) {
                // If a non-trivial character encountered, use altBase64
                //return toWindowsAlt64Name(javaName);
            	return call("toWindowsAlt64Name", new Class[] {String.class}, new Object[] {javaName});
            }
            if (ch == '\\') {
                windowsName.append("//");
            } else if (ch == '/') {
                windowsName.append('\\');
            } else {
                windowsName.append(ch);
            }
        }
        //return stringToByteArray(windowsName.toString());
    	return call("stringToByteArray", new Class[] {String.class}, new Object[] {windowsName.toString()});
    }

	@SuppressWarnings("unchecked")
	private <R> R call(String methodName, Class<?>[] types, Object[] args) {
		try {
			Method m = winPrefClazz.getDeclaredMethod(methodName, types);
			m.setAccessible(true);
			return (R)m.invoke(null, args);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private int[] callWindowsReg(Preferences pref, String methodName, int hKey, byte[] subKey, int securityMask) throws IOException {
		int[] result = call(methodName, 
				new Class[] {int.class, byte[].class, int.class}, 
				new Object[] {hKey, subKey, securityMask});
		

		int code = result[ERROR_CODE];
		String message = "Cannot execute " + methodName;
		
		Status.assertStatus(code, message);
		return result;
	}

	private int openKey(Hive hive, String path, int flags) throws IOException {
        //int[] result = WindowsRegOpenKey1(hive.id(), toWindowsName(path), KEY_ENUMERATE_SUB_KEYS| KEY_QUERY_VALUE);
		return callWindowsReg(null, "WindowsRegOpenKey", hive.id(), toBytes(path), flags)[NATIVE_HANDLE];
	}
	
	private boolean closeKey(int paramInt) {
		//int i = WindowsRegCloseKey(paramInt);
		int result = call("WindowsRegCloseKey", new Class[] {int.class}, new Object[] {paramInt});
		return result == 0;
	}	
	
	
	private byte[] toBytes(String str) {
		byte[] b = str.getBytes();
		byte[] bytes = new byte[b.length + 1];
		System.arraycopy(b, 0, bytes, 0, b.length);
		bytes[bytes.length - 1] = 0;
		return bytes;
	}
	
	
    private String[] splitPath(String path) {
    	String normalizedPath = path.replaceFirst("\\\\$", "");
		String parentPath = "";
		String name = normalizedPath;
		
		int lastBackSlash = normalizedPath.lastIndexOf("\\");
		if (lastBackSlash >= 0) {
			parentPath = normalizedPath.substring(0, lastBackSlash);
			name = normalizedPath.substring(lastBackSlash + 1);
		}
		
		return new String[] {parentPath, name};
    }
    
}
