# company-registration

Microservice supporting the company registration and corporation tax aspects of the Streamline Company Registration Legislation.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

## API

| Path                                                                               | Supported Methods | Description  |
| ---------------------------------------------------------------------------------- | ------------------| ------------ |
|```/corporation-tax-registration/:registrationId/accounts-preparation-date```       |        PUT        | Update the accounting end dates for the user associated with the supplied registrationId|

###PUT /corporation-tax-registration/:registrationID/accounts-preparation-date

    Responds with:


| Status        | Message       |
|:--------------|:--------------|
| 200           | OK            |
| 403           | Forbidden     |
| 404           | Not found     |


####Example of usage

PUT /corporation-tax-registration/:registrationID/accounts-preparation-date

with header :

Authorization Bearer fNAao9C4kTby8cqa6g75emw1DZIyA5B72nr9oKHHetE=

**Request body**

No request body required.

**Response body**

A ```200``` success response:

```json
{
    "businessEndDateChoice": "COMPANY_DEFINED",
    "businessEndDate": "2222-12-12",
}
```

The error scenarios will return an error document, for example:
```
{
    "statusCode": 404,
    "message":"An existing Corporation Tax Registration record was not found"
}
``` 

# Running the App

To run the app you can run the service manager profile ```sm2 -start SCRS_ALL``` then run the```./run.sh``` script

# Testing the App

To run the tests for the application, you can run: ```sbt coverage test it/test coverageReport```
