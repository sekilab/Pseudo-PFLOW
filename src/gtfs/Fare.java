package gtfs;

public class Fare {
    private String fareId;
    private double price;
    private String currencyType;
    private String paymentMethod;
    private String transfers;

    // Constructor
    public Fare(String fareId, double price, String currencyType, String paymentMethod, String transfers) {
        this.fareId = fareId;
        this.price = price;
        this.currencyType = currencyType;
        this.paymentMethod = paymentMethod;
        this.transfers = transfers;
    }

    public Fare(){

    }

    // Getters and Setters
    public String getFareId() {
        return fareId;
    }

    public void setFareId(String fareId) {
        this.fareId = fareId;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getCurrencyType() {
        return currencyType;
    }

    public void setCurrencyType(String currencyType) {
        this.currencyType = currencyType;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getTransfers() {
        return transfers;
    }

    public void setTransfers(String transfers) {
        this.transfers = transfers;
    }

    @Override
    public String toString() {
        return "Fare{" +
                "fareId='" + fareId + '\'' +
                ", price=" + price +
                ", currencyType='" + currencyType + '\'' +
                ", paymentMethod='" + paymentMethod + '\'' +
                ", transfers='" + transfers + '\'' +
                '}';
    }
}
