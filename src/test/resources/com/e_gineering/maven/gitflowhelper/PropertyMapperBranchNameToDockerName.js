/*

 Sample Mapping for Mapping a Branchname into a Docker Image Name

 Name konvention for Docker image Names see
(see https://docs.docker.com/engine/reference/commandline/tag/#extended-description)

An image name is made up of slash-separated name components, optionally prefixed by a
registry hostname. The hostname must comply with standard DNS rules, but may not contain
underscores. If a hostname is present, it may optionally be followed by a port number in
the format :8080. If not present, the command uses Docker’s public registry located at
registry-1.docker.io by default.
Name components may contain lowercase letters, digits and separators. A separator is
defined as a period, one or two underscores, or one or more hyphens. A name component
may not start or end with a separator.
*/

function log(msg) {
    //print(msg);
}

function toValidDockerNameChar(c) {
    if(c.match(/[a-z]/i)) {
        return c.toLowerCase();
    }
    if(c.match(/[0-9]/)) {
        return c;
    }
    // Special handling for German Umlaute
    switch(c) {
        case ".":
            return ".";
        case "-":
            return "-";
        case "ä":
            return "ae";
        case "Ä":
            return "ae";
        case "ü":
            return "ue";
        case "Ü":
            return "ue";
        case "ö":
            return "oe";
        case 'Ö':
            return "oe";
        case 'ß':
            return "ss";
        default:
            return "_";
    }
}
function branchNameToDockerImageName(branchName) {
    if(branchName.length === 0) {
        return "";
    }
    var ret = "";
    var firstChar = toValidDockerNameChar(branchName.charAt(0))
    var numDots = 0;
    var numUnderscores = 0;
    // A name component may not start or end with a separator.
    switch(firstChar) {
        case '.':
            ret += "a.";
            numDots++;
            break;
        case '_':
            ret += "a_";
            numUnderscores++;
            break;
        case '-':
            ret += "a-";
            break;
        default:
            ret += firstChar;
    }

    for(var i = 1; i < branchName.length; i++) {
        var nextChar = toValidDockerNameChar(branchName.charAt(i));
        switch(nextChar) {
            case '.':
                numDots++;
                if(numDots > 1) {
                    ret += "a";
                    numDots = 0;
                }
                ret += '.';
                break;
            case '_':
                numUnderscores++;
                if(numUnderscores > 2) {
                    ret += "a";
                    numUnderscores = 1;
                }
                ret += '_';
                break;
            default:
                ret += nextChar;
                numUnderscores = 0;
                numDots = 0;
        }
    }
    log("ret: " + ret);

    return ret;
}


function map(branchName, branchType) {
    log("branchName: [" + branchName + "], branchType=[" + branchType + "]");
    switch(branchType.toString()) {
        case 'OTHER':
            return branchNameToDockerImageName(branchName.trim());
        default:
            return "";
    }
}