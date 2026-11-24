package com.jaypay.utility;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.Objects;
import java.util.Random;

public class DummyDataGenerator {

    private static final String DB_URL = Objects.requireNonNullElse(System.getenv("SPRING_DATASOURCE_URL"), "jdbc:postgresql://localhost:5432/jaypay");
    private static final String DB_USER = Objects.requireNonNullElse(System.getenv("SPRING_DATASOURCE_USERNAME"), "jaypay");
    private static final String DB_PASSWORD = System.getenv("SPRING_DATASOURCE_PASSWORD");

    private static final String[] ADDRESSES = {"강남구", "관악구", "서초구"};

    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            generateDummyPaymentData(conn);

            conn.close();
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void generateDummyData(Connection conn) throws SQLException {
        String insertQuery = "INSERT INTO membership (membership_id, address, email, is_corp, is_valid, name) VALUES (?, ?, ?, ?, ?, ?)";
        Random random = new Random();

        PreparedStatement pstmt = conn.prepareStatement(insertQuery);

        int numberOfDummyData = 1000;

        for (int i = 3; i <= numberOfDummyData; i++) {
            pstmt.setLong(1, i);
            pstmt.setString(2, ADDRESSES[random.nextInt(ADDRESSES.length)]);
            pstmt.setString(3, "email_" + i + "@example.com");
            pstmt.setBoolean(4, random.nextBoolean());
            pstmt.setBoolean(5, random.nextBoolean());
            pstmt.setString(6, "User " + i);
            System.out.println(pstmt);
            pstmt.executeUpdate();
        }

        pstmt.close();
    }

    private static void generateDummyPaymentData (Connection conn) {
        Random random = new Random();

        try (conn) {
            String query = "INSERT INTO payment_request (payment_id, request_membership_id, request_price, franchise_id, franchise_fee_rate, payment_status, approved_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement preparedStatement = conn.prepareStatement(query);

            int numberOfTestData = 10;
            for (int i = 0; i < numberOfTestData; i++) {
                long paymentId = (random.nextInt(900) + 100L);
                String membershipId = "" + (random.nextInt(900) + 100);
                int price = (random.nextInt(9) + 1) * 1000;
                String franchiseId = "" + (random.nextInt(10) + 1L);
                String franchiseFeeRate = String.format("%.2f", random.nextDouble() * 5.0);
                int paymentStatus = 0;
                Date approvedAt = new Date(System.currentTimeMillis() - random.nextInt(10000000));

                preparedStatement.setLong(1, paymentId);
                preparedStatement.setString(2, membershipId);
                preparedStatement.setInt(3, price);
                preparedStatement.setString(4, franchiseId);
                preparedStatement.setString(5, franchiseFeeRate);
                preparedStatement.setInt(6, paymentStatus);
                preparedStatement.setDate(7, new java.sql.Date(approvedAt.getTime()));
                preparedStatement.executeUpdate();

            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

}