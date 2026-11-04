import Catalogo from './Catalogo'

const CAMPOS = [
  {
    nombre: 'nombre',
    etiqueta: 'Nombre',
    requerido: true,
    mostrarEnLista: false,
  },
  {
    nombre: 'tipoUbicacion',
    etiqueta: 'Tipo de ubicacion',
    requerido: true,
    mostrarEnLista: true,
  },
  { nombre: 'pabellon', etiqueta: 'Pabellon', mostrarEnLista: true },
  { nombre: 'piso', etiqueta: 'Piso', mostrarEnLista: true },
  { nombre: 'descripcion', etiqueta: 'Descripcion', mostrarEnLista: false },
]

export default function Ubicaciones() {
  return (
    <Catalogo
      titulo="Ubicaciones"
      subtitulo="Catalogo de lugares de la residencia universitaria. Todos los usuarios con sesion pueden consultarlo; escribir exige el modulo UBICACIONES."
      endpoint="/ubicaciones"
      modulo="UBICACIONES"
      campos={CAMPOS}
      formVacio={{
        nombre: '',
        tipoUbicacion: '',
        pabellon: '',
        piso: '',
        descripcion: '',
      }}
      etiquetaSingular="Ubicacion"
    />
  )
}
