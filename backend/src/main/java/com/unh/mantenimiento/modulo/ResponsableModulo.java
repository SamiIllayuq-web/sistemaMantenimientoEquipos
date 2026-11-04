package com.unh.mantenimiento.modulo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entidad ResponsableModulo: tabla puente de permisos.
 * Un usuario puede tener varios modulos; la PK es compuesta.
 */
@Entity
@Table(name = "responsable_modulo")
@IdClass(ResponsableModulo.ResponsableModuloId.class)
@Getter
@Setter
@NoArgsConstructor
public class ResponsableModulo {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id_responsable", length = 8)
    private String idResponsable;

    @Id
    @Column(name = "id_modulo")
    private Integer idModulo;

    /** PK compuesta: (dni, id_modulo). */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class ResponsableModuloId {
        private String idResponsable;
        private Integer idModulo;

        public ResponsableModuloId(String idResponsable, Integer idModulo) {
            this.idResponsable = idResponsable;
            this.idModulo = idModulo;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ResponsableModuloId that)) return false;
            return java.util.Objects.equals(idResponsable, that.idResponsable)
                    && java.util.Objects.equals(idModulo, that.idModulo);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(idResponsable, idModulo);
        }
    }
}
