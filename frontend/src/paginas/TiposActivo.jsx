import Catalogo from './Catalogo'

const CAMPOS = [
  {
    nombre: 'nombre',
    etiqueta: 'Nombre',
    requerido: true,
    mostrarEnLista: false,
  },
  {
    nombre: 'categoria',
    etiqueta: 'Categoria',
    tipo: 'select',
    requerido: true,
    mostrarEnLista: true,
    opciones: [
      { valor: 'EQUIPO', texto: 'EQUIPO' },
      { valor: 'INFRAESTRUCTURA', texto: 'INFRAESTRUCTURA' },
    ],
  },
  { nombre: 'descripcion', etiqueta: 'Descripcion', mostrarEnLista: false },
  {
    nombre: 'requiereMantenimiento',
    etiqueta: 'Mantenimiento',
    tipo: 'checkbox',
    etiquetaCheck: 'Requiere mantenimiento',
    mostrarEnLista: true,
  },
  { nombre: 'vidaUtilAnios', etiqueta: 'Vida util (anos)', tipo: 'number', min: 1, max: 100, mostrarEnLista: true },
]

export default function TiposActivo() {
  return (
    <Catalogo
      titulo="Tipos de activo"
      subtitulo="Catalogo de tipos de equipo e infraestructura. Todos los usuarios con sesion pueden consultarlo; escribir exige el modulo TIPOS_ACTIVO."
      endpoint="/tipos-activo"
      modulo="TIPOS_ACTIVO"
      campos={CAMPOS}
      formVacio={{
        nombre: '',
        categoria: 'EQUIPO',
        descripcion: '',
        requiereMantenimiento: true,
        vidaUtilAnios: null,
      }}
      etiquetaSingular="Tipo de activo"
    />
  )
}
