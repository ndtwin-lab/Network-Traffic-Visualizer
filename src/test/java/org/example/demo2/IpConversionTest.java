package org.example.demo2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;

public class IpConversionTest {
    
    @Test
    public void testLittleEndianToIpConversion() {
        // 測試一些已知的 little-endian 整數 IP 轉換（基於 testbed 真實數據）
        // Little-endian: 字節順序是反的
        
        // 1728161984 (0x6701A8C0) 應該轉換為 192.168.1.103
        // 0xC0 0xA8 0x01 0x67 -> 192.168.1.103
        long testIp1 = 1728161984L;
        String expected1 = "192.168.1.103";
        
        // 604088512 (0x2401A8C0) 應該轉換為 192.168.1.36
        // 0xC0 0xA8 0x01 0x24 -> 192.168.1.36
        long testIp2 = 604088512L;
        String expected2 = "192.168.1.36";
        
        // 3232235777 (0xC0A80101) 應該轉換為 1.1.168.192
        // 0x01 0x01 0xA8 0xC0 -> 1.1.168.192
        long testIp3 = 3232235777L;
        String expected3 = "1.1.168.192";
        
        // 創建一個 NetworkTopologyApp 實例來測試轉換方法
        NetworkTopologyApp app = new NetworkTopologyApp();
        
        // 使用反射來訪問私有方法
        try {
            java.lang.reflect.Method method = NetworkTopologyApp.class.getDeclaredMethod("convertLittleEndianToIp", long.class);
            method.setAccessible(true);
            
            String result1 = (String) method.invoke(app, testIp1);
            String result2 = (String) method.invoke(app, testIp2);
            String result3 = (String) method.invoke(app, testIp3);
            
            assertEquals(expected1, result1, "1728161984 should convert to 192.168.1.103");
            assertEquals(expected2, result2, "604088512 should convert to 192.168.1.36");
            assertEquals(expected3, result3, "3232235777 should convert to 1.1.168.192");
            
            System.out.println("IP conversion test passed!");
            System.out.println("1728161984 (0x6701A8C0) -> " + result1);
            System.out.println("604088512 (0x2401A8C0) -> " + result2);
            System.out.println("3232235777 (0xC0A80101) -> " + result3);
            
        } catch (Exception e) {
            fail("Failed to test IP conversion: " + e.getMessage());
        }
    }
    
    @Test
    public void testIpToLittleEndianConversion() {
        // 測試標準 IP 轉換為 little-endian 整數
        String testIp1 = "192.168.1.103";
        long expected1 = 1728161984L;  // 0x6701A8C0
        
        String testIp2 = "192.168.1.36";
        long expected2 = 604088512L;   // 0x2401A8C0
        
        String testIp3 = "1.1.168.192";
        long expected3 = 3232235777L;  // 0xC0A80101
        
        // 創建一個 NetworkTopologyApp 實例來測試轉換方法
        NetworkTopologyApp app = new NetworkTopologyApp();
        
        // 使用反射來訪問私有方法
        try {
            java.lang.reflect.Method method = NetworkTopologyApp.class.getDeclaredMethod("convertIpToLittleEndian", String.class);
            method.setAccessible(true);
            
            long result1 = (Long) method.invoke(app, testIp1);
            long result2 = (Long) method.invoke(app, testIp2);
            long result3 = (Long) method.invoke(app, testIp3);
            
            assertEquals(expected1, result1, "192.168.1.103 should convert to 1728161984");
            assertEquals(expected2, result2, "192.168.1.36 should convert to 604088512");
            assertEquals(expected3, result3, "1.1.168.192 should convert to 3232235777");
            
            System.out.println("IP to little-endian conversion test passed!");
            System.out.println("192.168.1.103 -> " + result1 + " (0x" + Long.toHexString(result1) + ")");
            System.out.println("192.168.1.36 -> " + result2 + " (0x" + Long.toHexString(result2) + ")");
            System.out.println("1.1.168.192 -> " + result3 + " (0x" + Long.toHexString(result3) + ")");
            
        } catch (Exception e) {
            fail("Failed to test IP to little-endian conversion: " + e.getMessage());
        }
    }
    
    @Test
    public void testRoundTripConversion() {
        // 測試往返轉換：little-endian -> 標準 IP -> little-endian
        long originalIp = 1728161984L;  // 0x6701A8C0
        String expectedStandard = "192.168.1.103";
        
        NetworkTopologyApp app = new NetworkTopologyApp();
        
        try {
            java.lang.reflect.Method toStandardMethod = NetworkTopologyApp.class.getDeclaredMethod("convertLittleEndianToIp", long.class);
            java.lang.reflect.Method toLittleEndianMethod = NetworkTopologyApp.class.getDeclaredMethod("convertIpToLittleEndian", String.class);
            
            toStandardMethod.setAccessible(true);
            toLittleEndianMethod.setAccessible(true);
            
            // little-endian -> 標準 IP
            String standardIp = (String) toStandardMethod.invoke(app, originalIp);
            assertEquals(expectedStandard, standardIp, "Round-trip conversion failed: little-endian -> standard");
            
            // 標準 IP -> little-endian
            long backToLittleEndian = (Long) toLittleEndianMethod.invoke(app, standardIp);
            assertEquals(originalIp, backToLittleEndian, "Round-trip conversion failed: standard -> little-endian");
            
            System.out.println("Round-trip conversion test passed!");
            System.out.println("Original: " + originalIp + " -> Standard: " + standardIp + " -> Back: " + backToLittleEndian);
            
        } catch (Exception e) {
            fail("Failed to test round-trip conversion: " + e.getMessage());
        }
    }
    
    @Test
    public void testOversizedValueConversion() {
        // 測試超出 32 位範圍的值（從 testbed 真實數據）
        // 應該提取低 32 位並正確轉換
        long oversizedValue = 1062258083825841L;  // 0x3C61E3BBF08B1
        
        // 低 32 位: 0x3BBF08B1 = 1002375345
        // Little-endian: 0xB1 0x08 0xBF 0x3B -> 177.8.191.59
        String expectedIp = "177.8.191.59";
        
        NetworkTopologyApp app = new NetworkTopologyApp();
        
        try {
            java.lang.reflect.Method method = NetworkTopologyApp.class.getDeclaredMethod("convertLittleEndianToIp", long.class);
            method.setAccessible(true);
            
            String result = (String) method.invoke(app, oversizedValue);
            assertEquals(expectedIp, result, "Oversized value should extract lower 32 bits and convert correctly");
            
            System.out.println("Oversized value conversion test passed!");
            System.out.println(oversizedValue + " (0x" + Long.toHexString(oversizedValue) + ") -> " + result);
            
        } catch (Exception e) {
            fail("Failed to test oversized value conversion: " + e.getMessage());
        }
    }
}




