
### Shallow Copy Generator

Kotlin data classes generate a .copy function automatically. This is a workaround generator for classes where variables 
are vars. It can be used on classes that rely on inheritance or are otherwise not data classes. All fields (including
inherited fields) will be included.

e.g.

```
@ShallowCopyable
class Customer() {
    var id: Int = 0
    var name: String? = null
}       
```

Will generate:

```
fun Customer.shallowCopy(
    id: Int = this.id,
    name: String? = this.name
) = Customer().apply {
    this.id = id
    this.name = name
}
```

