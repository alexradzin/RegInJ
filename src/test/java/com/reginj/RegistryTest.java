package com.reginj;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;


public class RegistryTest {
	@Test
	public void testRegHKLM() throws IOException {
		assertMandatoryList(
				new String[] {"SOFTWARE", "HARDWARE", "SECURITY", "SYSTEM"}, 
				Registry.getRegistry().keys(Registry.Hive.HKEY_LOCAL_MACHINE, ""));
	}
    
    
	
	@Test
	public void testRegJavaSoft() throws IOException {
		String[] keys = Registry.getRegistry().keys(Registry.Hive.HKEY_LOCAL_MACHINE, "Software\\JavaSoft");
		for (String key : keys) {
			assertTrue(key.startsWith("Java") || key.equals("Prefs"));
		}
	}
	
	@Test(expected = UnsupportedOperationException.class)
	public void testKeysHiveDoesNotExist() throws IOException {
		Registry.getRegistry().keys(Registry.Hive.HKEY_DYN_DATA, "DoesNotMatter");
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testValuesHiveDoesNotExist() throws IOException {
		Registry.getRegistry().values(Registry.Hive.HKEY_DYN_DATA, "DoesNotMatter");
	}

	@Test(expected = FileNotFoundException.class)
	public void testKeysPathDoesNotExist() throws IOException {
		Registry.getRegistry().keys(Registry.Hive.HKEY_LOCAL_MACHINE, "DoesNotExist");
	}

	@Test(expected = FileNotFoundException.class)
	public void testValuesPathDoesNotExist() throws IOException {
		Registry.getRegistry().values(Registry.Hive.HKEY_LOCAL_MACHINE, "DoesNotExist");
	}

	@Test
	public void testRegHKCU() throws IOException {
		assertMandatoryList(
				new String[] {"SOFTWARE", "Console", "Printers", "Network"}, 
				Registry.getRegistry().keys(Registry.Hive.HKEY_CURRENT_USER, ""));
	}

	@Test
	public void testMsWinValues() throws IOException {
		assertMandatoryList(
				new String[] {"CommonFilesDir", "ProgramFilesDir", "DevicePath"}, 
				Registry.getRegistry().values(Registry.Hive.HKEY_LOCAL_MACHINE, "Software\\Microsoft\\Windows\\CurrentVersion"));
		
		
	}
	
	@Test
	public void testGetStringValue() throws IOException {
		String programFiles = Registry.getRegistry().get(Registry.Hive.HKEY_LOCAL_MACHINE, "Software\\Microsoft\\Windows\\CurrentVersion", "ProgramFilesDir");
		assertEquals("C:\\Program Files", programFiles);
	}

	@Test(expected = FileNotFoundException.class)
	public void testGetStringPathDoesNotExist() throws IOException {
		Registry.getRegistry().get(Registry.Hive.HKEY_LOCAL_MACHINE, "DoesNotExist", "ProgramFilesDir");
	}
	
	
	
	@Test
	public void testCreateKey() throws IOException {
		testCreateKey(Registry.Hive.HKEY_CURRENT_USER, "Software", "MyTest");
	}

	/**
	 * Attempt to create key under HKLM can succeed or fail if current user does not have enough rights.
	 * In this case {@link AccessDeniedException} is expected.
	 * @throws IOException
	 */
	@Test
	public void testCreateKeyHKLM() throws IOException {
		try {
			testCreateKey(Registry.Hive.HKEY_LOCAL_MACHINE, "Software", "MyTest");
			// OK for older versions of Windows or if user is administrator
		} catch (AccessDeniedException e) {
			// OK if user is NOT an administrator
		}
	}
	
	private void testCreateKey(Registry.Hive hive, String path, String key) throws IOException {
		assertFalse(Arrays.asList(Registry.getRegistry().keys(hive, path)).contains(key));
		String fullPath = path + "\\" + key;
		Registry.getRegistry().createKey(hive, fullPath);
		assertTrue(Arrays.asList(Registry.getRegistry().keys(hive, path)).contains(key));
		Registry.getRegistry().removeKey(hive, fullPath);
	}
	
	@Test(expected = FileNotFoundException.class)
	public void testCreateKeyPathDoesNotExist() throws IOException {
		Registry.getRegistry().createKey(Registry.Hive.HKEY_CURRENT_USER, "DoesNotExist\\MyTest");
	}
	
	@Test(expected = FileNotFoundException.class)
	public void testCreateKeyHiveDoesNotExist() throws IOException {
		Registry.getRegistry().createKey(Registry.Hive.HKEY_PERFORMANCE_DATA, "DoesNotExist\\MyTest");
	}
	
	@Test
	public void testPutStringValue() throws IOException {
		try {
			// create key first
			Registry.getRegistry().createKey(Registry.Hive.HKEY_CURRENT_USER, "Software\\MyTest");
			assertTrue(Arrays.asList(Registry.getRegistry().keys(Registry.Hive.HKEY_CURRENT_USER, "Software")).contains("MyTest"));
			
			Registry.getRegistry().put(Registry.Hive.HKEY_CURRENT_USER, "Software\\MyTest", "test_value", "hello");
			
			String hello = Registry.getRegistry().get(Registry.Hive.HKEY_CURRENT_USER, "Software\\MyTest", "test_value");
			assertEquals("hello", hello);
		} finally {
			Registry.getRegistry().removeKey(Registry.Hive.HKEY_CURRENT_USER, "Software\\MyTest");
		}
	}
	
	@Test(expected = FileNotFoundException.class)
	public void testPutStringValuePathDoesNotExist() throws IOException {
		Registry.getRegistry().put(Registry.Hive.HKEY_CURRENT_USER, "Software\\DoesNotExist", "test_value", "hello");
	}

	@Test
	public void testRemoveKey() throws IOException {
		Registry.getRegistry().createKey(Registry.Hive.HKEY_CURRENT_USER, "Software\\MyTest");
		assertTrue(Arrays.asList(Registry.getRegistry().keys(Registry.Hive.HKEY_CURRENT_USER, "Software")).contains("MyTest"));
		Registry.getRegistry().removeKey(Registry.Hive.HKEY_CURRENT_USER, "Software\\MyTest");
		// check that key does not exist now.
		assertFalse(Arrays.asList(Registry.getRegistry().keys(Registry.Hive.HKEY_CURRENT_USER, "Software")).contains("MyTest"));
	}
	
	@Test
	public void testRemoveStringValue() throws IOException {
		try {
			// create key first
			Registry.getRegistry().createKey(Registry.Hive.HKEY_CURRENT_USER, "Software\\MyTest");
			assertTrue(Arrays.asList(Registry.getRegistry().keys(Registry.Hive.HKEY_CURRENT_USER, "Software")).contains("MyTest"));
			
			Registry.getRegistry().put(Registry.Hive.HKEY_CURRENT_USER, "Software\\MyTest", "test_value", "hello");
			
			assertEquals("hello", Registry.getRegistry().get(Registry.Hive.HKEY_CURRENT_USER, "Software\\MyTest", "test_value"));
			Registry.getRegistry().removeValue(Registry.Hive.HKEY_CURRENT_USER, "Software\\MyTest", "test_value");
			
			assertFalse(Arrays.asList(Registry.getRegistry().values(Registry.Hive.HKEY_CURRENT_USER, "Software\\MyTest")).contains("test_value"));
		} finally {
			Registry.getRegistry().removeKey(Registry.Hive.HKEY_CURRENT_USER, "Software\\MyTest");
		}
	}
	
	
	private void assertMandatoryList(String[] expected, String[] actual) {
		Set<String> actualSet = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		actualSet.addAll(Arrays.asList(actual));
		
		for (String value : expected) {
			assertTrue("Value " + value + " is not found", actualSet.contains(value));
		}
	}
}
