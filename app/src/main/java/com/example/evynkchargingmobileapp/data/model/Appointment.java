package com.example.evynkchargingmobileapp.data.model;

public class Appointment {
    private String id;
    private String ownerNic;
    private String reservationAtUtc;
    private String status;

    public Appointment(String id, String ownerNic, String reservationAtUtc, String status) {
        this.id = id;
        this.ownerNic = ownerNic;
        this.reservationAtUtc = reservationAtUtc;
        this.status = status;
    }

    public String getId() { return id; }
    public String getOwnerNic() { return ownerNic; }
    public String getReservationAtUtc() { return reservationAtUtc; }
    public String getStatus() { return status; }
}
