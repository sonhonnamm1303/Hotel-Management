/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controllers.receptionist;

import java.io.IOException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import models.Customer;
import models.Role;
import utility.Validation;
import dal.CustomerDAO;
import dal.RoomDAO;
import jakarta.servlet.http.HttpSession;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import models.Booking;
import models.BookingDetail;
import models.DetailService;
import models.PaymentMethod;
import models.Room;
import models.TypeRoom;
import utility.EmailService;
import utility.email_factory.EmailTemplateFactory.EmailType;

/**
 *
 * @author Hai Long
 */
@WebServlet(name = "Checkout", urlPatterns = {"/receptionist/checkout"})
public class Checkout extends HttpServlet {

    private static final ExecutorService emailExecutor = Executors.newFixedThreadPool(10);

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");

        String service = request.getParameter("service");

        HttpSession session = request.getSession(true);

        if (service == null) {
            service = "view";
        }

        if ("view".equals(service)) {
            String search = request.getParameter("search");

            System.out.println(search);

            if (search != null && !search.trim().isEmpty()) {

                String emailSearch = request.getParameter("emailSearch");

                if (!Validation.validateField(request, "searchError", emailSearch, "EMAIL", "Search", "Email không hợp lệ")) {

                    int customerId = CustomerDAO.getInstance().getCustomerIdByEmail(emailSearch);

                    Customer existedCustomer = CustomerDAO.getInstance().getCustomerByCustomerID(customerId);

                    if (existedCustomer != null) {
                        request.setAttribute("existedCustomer", existedCustomer);
                    } else if (emailSearch != null && !emailSearch.isEmpty()) {
                        request.setAttribute("newCustomer", "Khách hàng mới");
                        request.setAttribute("email", emailSearch);
                    }
                }
            }
            request.getRequestDispatcher("/View/Receptionist/Checkout.jsp").forward(request, response);

        }

        if ("addNew".equals(service)) {
            String[] roomNumbers = (String[]) session.getAttribute("roomNumbers");
            String startDate = (String) session.getAttribute("startDate");
            String endDate = (String) session.getAttribute("endDate");

            if (roomNumbers == null || roomNumbers.length == 0 || startDate == null || endDate == null) {
                request.setAttribute("errorMessage", "Thiếu thông tin đặt phòng. Vui lòng bắt đầu lại.");
                request.getRequestDispatcher("/View/Receptionist/SearchRoom.jsp").forward(request, response);
                return;
            }

            java.sql.Date startDateSql = java.sql.Date.valueOf(startDate);
            java.sql.Date endDateSql = java.sql.Date.valueOf(endDate);

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            String todayStr = sdf.format(new java.util.Date());
            if (startDate.compareTo(todayStr) < 0) {
                request.setAttribute("errorMessage", "Ngày nhận phòng phải từ hôm nay trở đi.");
                request.getRequestDispatcher("/View/Receptionist/SearchRoom.jsp").forward(request, response);
                return;
            }
            if (endDateSql.compareTo(startDateSql) <= 0) {
                request.setAttribute("errorMessage", "Ngày trả phòng phải sau ngày nhận phòng.");
                request.getRequestDispatcher("/View/Receptionist/SearchRoom.jsp").forward(request, response);
                return;
            }

            RoomDAO roomDAO = RoomDAO.getInstance();
            List<String> conflictRooms = roomDAO.getUnavailableRooms(List.of(roomNumbers), startDateSql, endDateSql);
            if (!conflictRooms.isEmpty()) {
                request.setAttribute("errorMessage", "Các phòng sau đã bị đặt trong thời gian đã chọn: " + String.join(", ", conflictRooms));
                session.removeAttribute("selectedRooms");
                request.getRequestDispatcher("/View/Receptionist/SearchRoom.jsp").forward(request, response);
                return;
            }

            String emailSearch = request.getParameter("emailSearch");

            String paymentMethod = request.getParameter("paymentMethod");

            String genderValue = request.getParameter("gender");

            String fullName = request.getParameter("fullName");

            String cccd = request.getParameter("cccd");

            boolean check = false;

            if (genderValue == null || genderValue.isEmpty()) {
                check = true;
                request.setAttribute("genderError", "Vui lòng chọn giới tính");
            }

            if (Validation.validateField(request, "nameError", fullName, "FULLNAME", "Họ tên", "Họ tên không hợp lệ")) {
                check = true;
            }

            if (Validation.validateField(request, "cccdError", cccd, "CCCD", "CCCD", "CCCD không hợp lệ")) {
                check = true;
            } else if (CustomerDAO.getInstance().checkcccd(cccd)) {
                check = true;
                request.setAttribute("cccdError", "CCCD đã tồn tại");
            }

            String phone = request.getParameter("phone");

            if (Validation.validateField(request, "phoneError", phone, "PHONE_NUMBER", "SĐT", "SDT không hợp lệ")) {
                check = true;
            } else if (CustomerDAO.getInstance().isPhoneExisted(phone)) {
                check = true;
                request.setAttribute("phoneError", "Số điện thoại đã tồn tại");
            }

            String email = request.getParameter("email");

            if (Validation.validateField(request, "emailError", email, "EMAIL", "email", "Email không hợp lệ")) {
                check = true;
            } else if (CustomerDAO.getInstance().checkExistedEmail(email)) {
                check = true;
                request.setAttribute("emailError", "Email đã tồn tại");
            }

            if ("default".equals(paymentMethod)) {
                check = true;
                request.setAttribute("paymentMethodError", "Vui lòng chọn phương thức thanh toán");
            }

            if (!check) {
                Customer customer = new Customer();
                customer.setFullName(fullName);
                customer.setCCCD(cccd);
                customer.setEmail(email);
                customer.setPhoneNumber(phone);
                customer.setRole(new Role(2));
                customer.setGender(genderValue.equals("Male"));
                customer.setActivate(true);

                int checkUpdate = CustomerDAO.getInstance().insertCustomerExceptionId(customer);
                if ("online".equals(paymentMethod)) {

                    session.setAttribute("customerId", checkUpdate);

                    session.setAttribute("status", "checkIn");

                    response.sendRedirect(request.getContextPath() + "/payment");
                }

                if ("offline".equals(paymentMethod)) {
                    checkInByMoney(request, response, checkUpdate);
                }
            } else {
                request.setAttribute("emailSearch", emailSearch);
                request.getRequestDispatcher("/View/Receptionist/Checkout.jsp").forward(request, response);
            }

        }

        if ("addExisted".equals(service)) {
            String[] roomNumbers = (String[]) session.getAttribute("roomNumbers");
            String startDate = (String) session.getAttribute("startDate");
            String endDate = (String) session.getAttribute("endDate");

            if (roomNumbers == null || roomNumbers.length == 0 || startDate == null || endDate == null) {
                request.setAttribute("errorMessage", "Thiếu thông tin đặt phòng. Vui lòng bắt đầu lại.");
                request.getRequestDispatcher("/View/Receptionist/SearchRoom.jsp").forward(request, response);
                return;
            }

            java.sql.Date startDateSql = java.sql.Date.valueOf(startDate);
            java.sql.Date endDateSql = java.sql.Date.valueOf(endDate);

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            String todayStr = sdf.format(new java.util.Date());
            if (startDate.compareTo(todayStr) < 0) {
                request.setAttribute("errorMessage", "Ngày nhận phòng phải từ hôm nay trở đi.");
                request.getRequestDispatcher("/View/Receptionist/SearchRoom.jsp").forward(request, response);
                return;
            }
            if (endDateSql.compareTo(startDateSql) <= 0) {
                request.setAttribute("errorMessage", "Ngày trả phòng phải sau ngày nhận phòng.");
                request.getRequestDispatcher("/View/Receptionist/SearchRoom.jsp").forward(request, response);
                return;
            }

            RoomDAO roomDAO = RoomDAO.getInstance();
            List<String> conflictRooms = roomDAO.getUnavailableRooms(List.of(roomNumbers), startDateSql, endDateSql);
            if (!conflictRooms.isEmpty()) {
                request.setAttribute("errorMessage", "Các phòng sau đã bị đặt trong thời gian đã chọn: " + String.join(", ", conflictRooms));
                session.removeAttribute("selectedRooms");
                request.getRequestDispatcher("/View/Receptionist/SearchRoom.jsp").forward(request, response);
                return;
            }
            String emailSearch = request.getParameter("emailSearch");

            String email = request.getParameter("email");

            String paymentMethod = request.getParameter("paymentMethod");

            int customerId = CustomerDAO.getInstance().getCustomerIdByEmail(email);

            Customer existedCustomer = CustomerDAO.getInstance().getCustomerByCustomerID(customerId);

            if (existedCustomer.getCCCD() == null) {
                boolean check = false;
                String cccd = request.getParameter("cccd");
                if (Validation.validateField(request, "cccdError", cccd, "CCCD", "CCCD", "CCCD không hợp lệ")) {
                    check = true;
                } else if (CustomerDAO.getInstance().checkcccd(cccd)) {
                    check = true;
                    request.setAttribute("cccdError", "CCCD đã tồn tại");
                }
                if (!check) {
                    CustomerDAO.getInstance().updateCustomerCCCD(cccd, existedCustomer.getCustomerId());
                } else {
                    request.setAttribute("existedCustomer", existedCustomer);
                    request.setAttribute("emailSearch", emailSearch);
                    request.getRequestDispatcher("/View/Receptionist/Checkout.jsp").forward(request, response);
                    return;
                }
            }

            if ("default".equals(paymentMethod)) {
                request.setAttribute("paymentMethodError", "Vui lòng chọn phương thức thanh toán");
                request.setAttribute("existedCustomer", existedCustomer);
                request.setAttribute("emailSearch", emailSearch);
                request.getRequestDispatcher("/View/Receptionist/Checkout.jsp").forward(request, response);
                return;
            }

            if ("online".equals(paymentMethod)) {

                session.setAttribute("customerId", existedCustomer.getCustomerId());

                session.setAttribute("status", "checkIn");

                response.sendRedirect(request.getContextPath() + "/payment");
            }

            if ("offline".equals(paymentMethod)) {
                checkInByMoney(request, response, existedCustomer.getCustomerId());
            }
        }

        if ("backToServicePage".equals(service)) {
            session.removeAttribute("roomServiceMap");

            response.sendRedirect("receptionist/roomInformation");
        }

    }

    private void sendEmail(HttpServletRequest request, HttpServletResponse response, int customerId, int bookingId)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        Customer customer = dal.CustomerDAO.getInstance().getCustomerById(customerId);
        String customerName = customer.getFullName();
        String email = customer.getEmail();
        String phone = customer.getPhoneNumber();
        String startDateStr = (String) session.getAttribute("startDate");
        String endDateStr = (String) session.getAttribute("endDate");
        String[] roomNumbers = (String[]) session.getAttribute("roomNumbers");
//        Map<String, List<DetailService>> roomServicesMap
//                = (Map<String, List<DetailService>>) session.getAttribute("roomServicesMap");

        // Sau khi đã insert BookingDetail và DetailService
        // Tạo các list cần thiết
        List<String> typeRoom = new ArrayList<>();
        List<Integer> quantityTypeRoom = new ArrayList<>();
        List<Integer> priceTypeRoom = new ArrayList<>();
        List<String> services = new ArrayList<>();
        List<Integer> serviceQuantity = new ArrayList<>();
        List<Integer> servicePrice = new ArrayList<>();

        // Tạo map đếm loại phòng
        Map<String, Integer> typeCountMap = new LinkedHashMap<>();
        Map<String, Integer> typePriceMap = new LinkedHashMap<>();
        Map<String, Integer> serviceQuantityMap = new LinkedHashMap<>();
        Map<String, Integer> servicePriceMap = new LinkedHashMap<>();

        long numberOfNights = 1; // default
        if (startDateStr != null && endDateStr != null) {
            Date startDate = Date.valueOf(startDateStr);
            Date endDate = Date.valueOf(endDateStr);
            numberOfNights = (endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24);
        }

        //đây là chỗ tìm và đếm
        for (String roomNumberStr : roomNumbers) {
            int roomNumber = Integer.parseInt(roomNumberStr);
            Room room = dal.RoomDAO.getInstance().getRoomByRoomNumber(roomNumber);
            int typeId = room.getTypeRoom().getTypeId();
            TypeRoom type = dal.TypeRoomDAO.getInstance().getTypeRoomById(typeId);

            String typeName = type.getTypeName();
            int price = type.getPrice();

            if (!typeCountMap.containsKey(typeName)) {
                typeCountMap.put(typeName, 1);
                typePriceMap.put(typeName, price);
            } else {
                typeCountMap.put(typeName, typeCountMap.get(typeName) + 1);
            }
        }

        //chỗ ghi list đây
        for (String typeName : typeCountMap.keySet()) {
            int quantity = typeCountMap.get(typeName);
            int unitPrice = typePriceMap.get(typeName);
            int totalPrice = (int) (quantity * unitPrice * numberOfNights);
            typeRoom.add(typeName);
            quantityTypeRoom.add(quantity);
            priceTypeRoom.add(totalPrice);
        }

        // Lấy danh sách BookingDetail
        List<BookingDetail> bookingDetails = dal.BookingDetailDAO.getInstance().getBookingDetailsByBookingId(bookingId);

        for (BookingDetail bd : bookingDetails) {
            int bdId = bd.getBookingDetailId();
            List<DetailService> servicesList = dal.DetailServiceDAO.getInstance().getServicesByBookingDetailId(bdId);
            for (DetailService d : servicesList) {
                String serviceName = d.getService().getServiceName();
                int quantity = d.getQuantity();
                int priceAtTime = d.getPriceAtTime();

                serviceQuantityMap.put(serviceName, serviceQuantityMap.getOrDefault(serviceName, 0) + quantity);
                servicePriceMap.put(serviceName, servicePriceMap.getOrDefault(serviceName, 0) + priceAtTime);
            }
        }

        for (String name : serviceQuantityMap.keySet()) {
            services.add(name);
            serviceQuantity.add(serviceQuantityMap.get(name));
            servicePrice.add(servicePriceMap.get(name));
        }

        //tổng tiền
        int total = 0;
        Object obj = session.getAttribute("totalPrice");
        if (obj instanceof Double) {
            total = ((Double) obj).intValue();
        }

        //tên phương thức thanh toán
        String paymentMethod = dal.BookingDAO.getInstance().getPaymentNameByBookingId(bookingId);

        Map<String, Object> data = new HashMap<>();
        data.put("customerName", customerName);
        data.put("email", email);
        data.put("phone", phone);
        data.put("startDate", startDateStr);
        data.put("endDate", endDateStr);
        data.put("total", total);
        data.put("paymentMethod", paymentMethod);
        data.put("typeRoom", typeRoom);
        data.put("quantityTypeRoom", quantityTypeRoom);
        data.put("priceTypeRoom", priceTypeRoom);
        data.put("services", services);
        data.put("serviceQuantity", serviceQuantity);
        data.put("servicePrice", servicePrice);
        emailExecutor.submit(() -> {
            System.out.println("Sending email to " + email);
            EmailService emailService = new EmailService();
            emailService.sendEmail(email, "Confirm Checkin information", EmailType.CHECKIN, data);
        });

        session.removeAttribute("paidAmount");
        session.removeAttribute("bookingDetailId");
        session.removeAttribute("listService");
        session.removeAttribute("roomServicesMap");
        session.removeAttribute("roomNumbers");
    }

    private void checkInByMoney(HttpServletRequest request, HttpServletResponse response, int customerId)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        int totalPrice = 0;
        int bookingId = 0;
        Object obj = session.getAttribute("totalPrice");
        if (obj instanceof Double) {
            totalPrice = ((Double) obj).intValue();
        }
        Booking booking = new Booking();
        Customer customer = new Customer();
        customer.setCustomerId(customerId);
        booking.setCustomer(customer);
        PaymentMethod paymentMehtodCheckIn = new PaymentMethod();
        paymentMehtodCheckIn.setPaymentMethodId(2);
        booking.setPaymentMethodCheckIn(paymentMehtodCheckIn);
        bookingId = dal.BookingDAO.getInstance().insertNewBooking(booking);

        Booking newBooking = new Booking();
        newBooking.setBookingId(bookingId);
        newBooking.setPaidAmount(totalPrice);
        dal.BookingDAO.getInstance().updateBookingPaidAmount(newBooking);
        newBooking.setStatus("Completed CheckIn");
        dal.BookingDAO.getInstance().updateBookingStatus(newBooking);

        String startDateStr = (String) session.getAttribute("startDate");
        String endDateStr = (String) session.getAttribute("endDate");
        Date startDate = Date.valueOf(startDateStr);
        Date endDate = Date.valueOf(endDateStr);
        String[] roomNumbers = (String[]) session.getAttribute("roomNumbers");
        long numberOfNights = (endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24);

        // Lấy Map roomServicesMap
        Map<String, List<DetailService>> roomServicesMap
                = (Map<String, List<DetailService>>) session.getAttribute("roomServicesMap");

        // Lấy danh sách dịch vụ đã bao gồm sẵn theo loại phòng
        Map<String, List<DetailService>> includedServiceQuantities
                = (Map<String, List<DetailService>>) session.getAttribute("includedServiceQuantities");

        for (String roomNumberStr : roomNumbers) {
            int roomNumber = Integer.parseInt(roomNumberStr);
            // Lấy giá phòng theo từng room
            Room roomObj = dal.RoomDAO.getInstance().getRoomByRoomNumber(roomNumber);
            int typeId = roomObj.getTypeRoom().getTypeId();
            double roomPrice = dal.TypeRoomDAO.getInstance().getPriceByTypeId(typeId);
            double totalRoomPrice = roomPrice * numberOfNights;

            // Tính tổng tiền dịch vụ (nếu có)
            int totalServiceCost = 0;
            if (roomServicesMap != null && roomServicesMap.containsKey(roomNumberStr)) {
                List<DetailService> serviceList = roomServicesMap.get(roomNumberStr);
                List<DetailService> includedList = includedServiceQuantities != null
                        ? includedServiceQuantities.getOrDefault(roomNumberStr, new ArrayList<>())
                        : new ArrayList<>();

                for (DetailService detail : serviceList) {
                    int serviceId = detail.getService().getServiceId();
                    int quantity = detail.getQuantity();
                    int priceAtTime = detail.getService().getPrice();

                    // Tìm quantity của dịch vụ này trong danh sách included
                    int includedQuantity = 0;
                    for (DetailService included : includedList) {
                        if (included.getService().getServiceId() == serviceId) {
                            includedQuantity = included.getQuantity();
                            break;
                        }
                    }

                    int finalQuantity = quantity - includedQuantity;
                    if (finalQuantity > 0) {
                        int total = finalQuantity * priceAtTime;
                        totalServiceCost += total;
                    }
                }
            }

            // Tổng tiền phòng + dịch vụ
            int totalAmount = (int) Math.round(totalRoomPrice) + totalServiceCost;

            // Tạo BookingDetail
            BookingDetail bookingDetail = new BookingDetail();
            Room room = new Room();
            Booking booking1 = new Booking();
            bookingDetail.setStartDate(startDate);
            bookingDetail.setEndDate(endDate);
            room.setRoomNumber(roomNumber);
            bookingDetail.setRoom(room);
            booking1.setBookingId(bookingId);
            bookingDetail.setBooking(booking1);
            bookingDetail.setTotalAmount(totalAmount);
            int bookingDetailId = dal.BookingDetailDAO.getInstance().insertNewBookingDetail(bookingDetail);

            // Chèn các dịch vụ phát sinh thực sự phải tính tiền
            if (roomServicesMap != null && roomServicesMap.containsKey(roomNumberStr)) {
                List<DetailService> serviceList = roomServicesMap.get(roomNumberStr);
                List<DetailService> includedList = includedServiceQuantities != null
                        ? includedServiceQuantities.getOrDefault(roomNumberStr, new ArrayList<>())
                        : new ArrayList<>();

                for (DetailService detail : serviceList) {
                    int serviceId = detail.getService().getServiceId();
                    int quantity = detail.getQuantity();
                    int priceAtTime = detail.getService().getPrice();

                    // Tìm quantity của dịch vụ này trong danh sách included
                    int includedQuantity = 0;
                    for (DetailService included : includedList) {
                        if (included.getService().getServiceId() == serviceId) {
                            includedQuantity = included.getQuantity();
                            break;
                        }
                    }

                    int finalQuantity = quantity - includedQuantity;
                    if (finalQuantity >= 0) {
                        int total = finalQuantity * priceAtTime;

                        dal.DetailServiceDAO.getInstance().insertDetailService(
                                bookingDetailId, serviceId, quantity, total
                        );
                    }
                }
            }
        }

        sendEmail(request, response, customerId, bookingId);

        response.sendRedirect(request.getContextPath() + "/receptionist/stayingRoom");
//                    session.removeAttribute("totalPrice");
    }

//    private boolean checkRoomConflict(HttpServletRequest request, HttpServletResponse response, HttpSession session)
//            throws ServletException, IOException {
//
//        String[] roomNumbers = (String[]) session.getAttribute("roomNumbers");
//        String startDate = (String) session.getAttribute("startDate");
//        String endDate = (String) session.getAttribute("endDate");
//
//        if (roomNumbers != null && startDate != null && endDate != null) {
//            java.sql.Date startDateSql = java.sql.Date.valueOf(startDate);
//            java.sql.Date endDateSql = java.sql.Date.valueOf(endDate);
//
//            List<String> conflictRooms = RoomDAO.getInstance().getUnavailableRooms(
//                    java.util.Arrays.asList(roomNumbers), startDateSql, endDateSql
//            );
//
//            if (!conflictRooms.isEmpty()) {
//                request.setAttribute("roomConflictError", "Phòng đã bị đặt: " + String.join(", ", conflictRooms));
//                session.removeAttribute("selectedRooms");
//                request.getRequestDispatcher("/View/Receptionist/SearchRoom.jsp").forward(request, response);
//                return true;
//            }
//        }
//        return false;
//    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
