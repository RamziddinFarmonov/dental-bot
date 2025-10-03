package com.example.dentalbot.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServiceRepository {
    private final DatabaseManager dbManager = DatabaseManager.getInstance();

    public static class Service {
        private int id;
        private String name;
        private int minPrice;  // Yangi: minimum narx
        private int maxPrice;  // Yangi: maksimum narx
        private boolean active;

        public Service(int id, String name, int minPrice, int maxPrice, boolean active) {
            this.id = id;
            this.name = name;
            this.minPrice = minPrice;
            this.maxPrice = maxPrice;
            this.active = active;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getMinPrice() {
            return minPrice;
        }  // Yangi

        public int getMaxPrice() {
            return maxPrice;
        }  // Yangi

        public boolean isActive() {
            return active;
        }

        // Narxni formatlangan ko'rinishda olish
        public String getPriceRange() {
            if (minPrice == 0 && maxPrice == 0) {
                return "Bepul";
            }
            return minPrice + " - " + maxPrice + " so'm";
        }
    }

    public List<Service> getAllServices() {
        List<Service> services = new ArrayList<>();
        String sql = "SELECT * FROM services WHERE active = 1 ORDER BY id";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                services.add(new Service(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("min_price"),  // Yangi
                        rs.getInt("max_price"),  // Yangi
                        rs.getBoolean("active")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return services;
    }

    public Service getServiceById(int id) {
        String sql = "SELECT * FROM services WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new Service(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("min_price"),  // Yangi
                        rs.getInt("max_price"),  // Yangi
                        rs.getBoolean("active")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean addService(String name, int minPrice, int maxPrice) {  // Yangi
        String sql = "INSERT INTO services (name, min_price, max_price) VALUES (?, ?, ?)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            pstmt.setInt(2, minPrice);
            pstmt.setInt(3, maxPrice);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateService(int id, String name, int minPrice, int maxPrice) {  // Yangi
        String sql = "UPDATE services SET name = ?, min_price = ?, max_price = ? WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            pstmt.setInt(2, minPrice);
            pstmt.setInt(3, maxPrice);
            pstmt.setInt(4, id);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }



    public boolean deleteService(int id) {
        String sql = "UPDATE services SET active = 0 WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}