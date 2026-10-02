package com.codevam.vecindad.billing.application;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.function.Supplier;

/**
 * Transacción JDBC propia de las operaciones financieras (varias sentencias atómicas: o todo o nada).
 * Usa un gestor privado (no registrado como bean) para no interferir con el de JPA. Todo SQL va calificado con el schema del tenant.
 */
@Component
public class FinanceTx {
    private final TransactionTemplate template;

    public FinanceTx(DataSource dataSource) {
        this.template = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public <T> T run(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
