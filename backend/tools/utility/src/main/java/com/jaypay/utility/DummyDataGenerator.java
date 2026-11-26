package com.jaypay.utility;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

public class DummyDataGenerator {

    private static final String DB_URL = Objects.requireNonNullElse(System.getenv("SPRING_DATASOURCE_URL"), "jdbc:postgresql://localhost:5432/jaypay");
    private static final String DB_USER = Objects.requireNonNullElse(System.getenv("SPRING_DATASOURCE_USERNAME"), "jaypay");
    private static final String DB_PASSWORD = System.getenv("SPRING_DATASOURCE_PASSWORD");

    private static final String[] ADDRESSES = {"Gangnam", "Gwanak", "Seocho"};

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
            String query = "INSERT INTO payment (request_id, request_membership_id, franchise_id, request_price, fee_rate_basis_points, fee, payout, payment_status, requested_at, approved_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'PAID', ?, ?)";
            PreparedStatement preparedStatement = conn.prepareStatement(query);

            int numberOfTestData = 10;
            int feeRateBasisPoints = 300;
            for (int i = 0; i < numberOfTestData; i++) {
                long membershipId = random.nextInt(900) + 100L;
                long franchiseId = random.nextInt(10) + 1L;
                long price = (random.nextInt(9) + 1) * 1000L;
                long fee = price * feeRateBasisPoints / 10000;
                java.sql.Timestamp approvedAt = new java.sql.Timestamp(System.currentTimeMillis() - random.nextInt(10000000));

                preparedStatement.setString(1, "dummy-" + UUID.randomUUID());
                preparedStatement.setLong(2, membershipId);
                preparedStatement.setLong(3, franchiseId);
                preparedStatement.setLong(4, price);
                preparedStatement.setInt(5, feeRateBasisPoints);
                preparedStatement.setLong(6, fee);
                preparedStatement.setLong(7, price - fee);
                preparedStatement.setTimestamp(8, approvedAt);
                preparedStatement.setTimestamp(9, approvedAt);
                preparedStatement.executeUpdate();

            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

}