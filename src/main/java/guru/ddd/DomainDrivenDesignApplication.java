//:guru.ddd.ModulithApplication.java;

package guru.ddd;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;


@EnableAsync
@SpringBootConfiguration
@EnableAutoConfiguration
@Modulithic(systemName = "ddd")
@ComponentScan({ "guru.ddd.config" })
public class DomainDrivenDesignApplication {

    public static void main(String[] args) {
        SpringApplication.run(DomainDrivenDesignApplication.class, args);
    }

}
///:~