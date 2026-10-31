//: guru.ddd.config.DomainDrivenDesignCfg.java

package guru.ddd.config;


import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@Configuration
@ComponentScan({
        "guru.ddd.identityaccess.config",
        "guru.ddd.collaboration.config",
        "guru.ddd.agilepm.config" })
class DomainDrivenDesignCfg {

} /// :~