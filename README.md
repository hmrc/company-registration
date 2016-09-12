# company-registration

[![Build Status](https://travis-ci.org/hmrc/company-registration.svg)](https://travis-ci.org/hmrc/company-registration) [ ![Download](https://api.bintray.com/packages/hmrc/releases/company-registration/images/download.svg) ](https://bintray.com/hmrc/releases/company-registration/_latestVersion)

Microservice supporting the company registration aspects of the Streamline Company Registration Legislation.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")

## API

| Path                                                                               | Supported Methods | Description  |
| ---------------------------------------------------------------------------------- | ------------------| ------------ |
| ...                                                                                |        ...        | ...          |
|```/corporation-tax-registration/:registrationID/ch-handoff-data```                 |        GET        | Get the handoff data for Companies House |
|```/corporation-tax-registration/:registrationID/ch-handoff-data```                 |        PUT        | Store the handoff data for Companies House |

###GET /corporation-tax-registration/:registrationID/ch-handoff-data

    Responds with:


| Status        | Message       |
|:--------------|:--------------|
| 200           | OK            |
| 403           | Forbidden     |
| 404           | Not found     |

    The response body is a valid Json with the following keys:


####Example of usage

GET /corporation-tax-registration/12345/ch-handoff-data

with header:

Authorization Bearer fNAao9C4kTby8cqa6g75emw1DZIyA5B72nr9oKHHetE=

**Response body**

A ```200``` success response will return the hand-off information, e.g. :

```json
{
    "handoffType": "basic-data",
    "handoffTime": "2016-10-10T15:44:17.496Z",
    "data": {
      "foo" : "bar"
    }
}
```
The ```handoffType``` should contain one of :
* `basic-data` - reflecting the hand off of basic company information
* `full-data` - relecting


The error scenarios will return an error document, for example :
```
{
    "statusCode":"404",
    "message":"Could not find CH handoff data"
}
```


###PUT /corporation-tax-registration/:registrationID/ch-handoff-data

    Responds with:


| Status        | Message       |
|:--------------|:--------------|
| 200           | OK            |
| 403           | Forbidden     |
| 404           | Not found     |

    The response body is a valid Json with the following keys:


####Example of usage

PUT /corporation-tax-registration/12345/ch-handoff-data

with header:

Authorization Bearer fNAao9C4kTby8cqa6g75emw1DZIyA5B72nr9oKHHetE=

**Request body**

```json
{
    "handoff-type": "basic-data",
    "handoff-time": "2016-10-10T15:44:17.496Z",
    "data": {
      "foo" : "bar"
    }
}
```

**Response body**

A ```200``` success response has no relevant body content.

The error scenarios will return an error document, for example :
```
{
    "statusCode":"404",
    "message":"Could not find CH handoff data"
}
```
