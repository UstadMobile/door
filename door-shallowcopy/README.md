
### Shallow Copy Generator

Kotlin data classes generate a .copy function automatically. This is a workaround generator for classes where variables 
are vars. It can be used on classes that rely on inheritance or are otherwise not data classes. All fields (including
inherited fields) will be shallow copied.

e.g. given a class:

```
class Customer() {
    var id: Int = 0
    var name: String? = null
}       
```

Create an expect function:
```
expect fun Customer.shallowCopy(
    //optional block function
    block: Customer.() -> Unit,
): Customer
```


Will generate:

```
actual fun Customer.shallowCopy(
    block: Customer.() -> Unit
) = Customer().also {
    it.id = this.id
    it.name = this.name
    block(it)
}
```

Which could then be used like this:

```
val updatedCustomer = customer.shallowCopy {
    name = "NewName"
}
```