package com.jaypay.utility;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.Random;

public class DummyDataGenerator {

    // MySQL 데이터베이스 연결 정보
    private static final String DB_URL = "jdbc:mysql://localhost:3306/jaypay"; // 데이터베이스 URL
    private static final String DB_USER = "mysqluser"; // 데이터베이스 사용자명
    private static final String DB_PASSWORD = "mysqlpw"; // 데이터베이스 비밀번호

    // 샘플 주소 데이터 배열
    private static final String[] ADDRESSES = {"강남구", "관악구", "서초구"};

    public static void main(String[] args) {
        try {
            // JDBC 드라이버 로드
            Class.forName("com.mysql.cj.jdbc.Driver");

            // 데이터베이스 연결
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);

            // 더미 데이터 생성
//            generateDummyData(conn);
            generateDummyPaymentData(conn);

            // 연결 종료
            conn.close();
        } catch (ClassNotFoundException | SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private static void generateDummyData(Connection conn) throws SQLException {
        String insertQuery = "INSERT INTO membership (membership_id, address, email, is_corp, is_valid, name) VALUES (?, ?, ?, ?, ?, ?)";
        Random random = new Random();

        //Preparing PreparedStatement
        PreparedStatement pstmt = conn.prepareStatement(insertQuery);

        // A count of dummy data
        int numberOfDummyData = 1000;

        for (int i = 3; i <= numberOfDummyData; i++) {
            pstmt.setLong(1, i); // membership_id
            pstmt.setString(2, ADDRESSES[random.nextInt(ADDRESSES.length)]); // address (랜덤하게 선택)
            pstmt.setString(3, "email_" + i + "@example.com"); // email (일단은 간단하게 생성)
            pstmt.setBoolean(4, random.nextBoolean()); // is_corp (true 또는 false 랜덤하게 선택)
            pstmt.setBoolean(5, random.nextBoolean()); // is_valid (true 또는 false 랜덤하게 선택)
            pstmt.setString(6, "User " + i); // name
            System.out.println(pstmt);
            // Execute a query
            pstmt.executeUpdate();
        }

        // Close preparedStatement
        pstmt.close();
    }

    private static void generateDummyPaymentData (Connection conn) {
        Random random = new Random();

        try (conn) {
            String query = "INSERT INTO payment_request (payment_id, request_membership_id, request_price, franchise_id, franchise_fee_rate, payment_status, approved_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement preparedStatement = conn.prepareStatement(query);

            int numberOfTestData = 10;
            for (int i = 0; i < numberOfTestData; i++) {
                // 랜덤 값 생성
                long paymentId = (random.nextInt(900) + 100L); // 100 ~ 999
                String membershipId = "" + (random.nextInt(900) + 100); // 100 ~ 999
                int price = (random.nextInt(9) + 1) * 1000; // 1000 ~ 9000
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