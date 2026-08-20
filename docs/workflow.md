# Complete System Workflow Specification

**Institution**: Noida Institute of Engineering and Technology (NIET)  

---

```
[ Faculty Submits Achievement ]
               |
               v
  ( Status = PENDING )
               |
               +---> Upload PDF Proof Certificate ( Magic-Byte Validated )
               |
               v
 [ Notification Sent to HOD / Admin ]
               |
               v
   [ HOD / Admin Reviews Record ]
               |
        +------+------+
        |             |
        v             v
  [ APPROVE ]     [ REJECT ]
        |             |
        |             +---> Requires Feedback Comment
        |             |
        v             v
 ( APPROVED )    ( REJECTED )
        |             |
        +------+------+
               |
               v
 [ Notification Sent to Faculty Owner ]
               |
               v
 [ Immutable Audit Log Created (LOGIN / CRUD / VERIFICATION) ]
```
