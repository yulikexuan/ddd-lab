//: guru.ddd.helpdesk.config.HelpdeskCfg.java

package guru.ddd.helpdesk.config;


import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@Configuration
@ComponentScan({
        "guru.ddd.helpdesk.domain.repository",
        "guru.ddd.helpdesk.domain.service" })
class HelpdeskCfg {

}