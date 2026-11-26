package vn.floodconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication // tác vụ định kỳ được bật ở config/SchedulingConfig (tắt được cho kiểm thử tích hợp)
public class FloodConnectApplication {
    public static void main(String[] args) {
        SpringApplication.run(FloodConnectApplication.class, args);
    }
}
